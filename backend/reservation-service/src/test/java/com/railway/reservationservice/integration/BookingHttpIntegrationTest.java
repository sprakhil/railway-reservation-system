package com.railway.reservationservice.integration;

import com.jayway.jsonpath.JsonPath;
import com.railway.reservationservice.repository.*;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Real HTTP, filters, controllers, Feign/RestClient, services and H2 persistence.
 * Only remote catalogue/PNR/auth endpoints are HTTP stubs; no live provider or email is used. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:booking-http;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "railway.session.check-enabled=true"
})
class BookingHttpIntegrationTest {
    private static final String SECRET="test-only-signing-key-at-least-thirty-two-characters-long";
    private static final String INTERNAL="test-only-internal-token-at-least-32-characters";
    private static final String PASSENGER="{\"name\":\"Demo Traveller\",\"age\":25,\"gender\":\"OTHER\",\"birthPreference\":\"LOWER\"}";
    private static final String TRAIN="""
        {"trainNumber":"12345","trainName":"Demo","seatClasses":["SL"],
         "operatingDays":["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"],
         "classConfigurations":[{"seatClass":"SL","coachCount":1,"seatsPerCoach":2,
           "baseFare":20,"ratePerKm":0.50,"minimumFare":40}]}
        """;
    private static final String ROUTE="""
        [{"stationCode":"AAA","stopSequence":1,"distanceKm":0,"dayNumber":1,"departureTime":"10:00:00"},
         {"stationCode":"BBB","stopSequence":2,"distanceKm":100,"dayNumber":1,"arrivalTime":"11:00:00","departureTime":"11:05:00"},
         {"stationCode":"CCC","stopSequence":3,"distanceKm":200,"dayNumber":1,"arrivalTime":"12:00:00"}]
        """;
    private static final AtomicLong PNRS=new AtomicLong();
    private static final AtomicInteger CATALOG_CALLS=new AtomicInteger();
    private static final AtomicBoolean SESSION_ACTIVE=new AtomicBoolean(true);
    private static final AtomicBoolean FORWARDED_AUTH=new AtomicBoolean();
    private static final HttpServer DEPENDENCIES=startDependencies();
    @LocalServerPort int port;
    @Autowired ReservationRepository reservations;
    @Autowired SeatInventoryRepository inventories;
    @Autowired BookingNotificationRepository notifications;
    @Autowired UserNotificationRepository inbox;
    @MockitoBean Clock clock;
    private final HttpClient http=HttpClient.newHttpClient();
    private Instant now;

    private static HttpServer startDependencies() {
        try {
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/", exchange->{
                String path=exchange.getRequestURI().getPath();
                String body; String type="application/json"; int status=200;
                if(path.equals("/api/trains")) {
                    CATALOG_CALLS.incrementAndGet();
                    body="{\"content\":["+TRAIN+"],\"page\":0,\"size\":100,\"totalElements\":1,\"totalPages\":1}";
                } else if(path.equals("/api/trains/12345")) { CATALOG_CALLS.incrementAndGet(); body=TRAIN; }
                else if(path.equals("/api/schedules/12345")) body=ROUTE;
                else if(path.equals("/api/pnr/generate")) {
                    FORWARDED_AUTH.set(exchange.getRequestHeaders().getFirst("Authorization")!=null);
                    type="text/plain"; body=Long.toString(PNRS.incrementAndGet());
                } else if(path.equals("/internal/auth/session-check")) {
                    String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                    boolean valid=INTERNAL.equals(exchange.getRequestHeaders().getFirst("X-Service-Token"))
                        && request.contains("\"sid\"") && request.contains("\"username\"");
                    body="{\"active\":"+(valid && SESSION_ACTIVE.get())+"}";
                } else {status=404;body="{}";}
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type",type);
                exchange.sendResponseHeaders(status,bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start(); return server;
        } catch(java.io.IOException ex) {throw new ExceptionInInitializerError(ex);}
    }
    @DynamicPropertySource static void remoteUrls(DynamicPropertyRegistry properties) {
        String url="http://127.0.0.1:"+DEPENDENCIES.getAddress().getPort();
        for(String name:List.of("train-service","schedule-service","pnr-service"))
            properties.add("spring.cloud.openfeign.client.config."+name+".url",()->url);
        properties.add("railway.auth.url",()->url);
    }
    @AfterAll static void stopDependencies(){DEPENDENCIES.stop(0);}
    @BeforeEach void setup() {
        inbox.deleteAll(); notifications.deleteAll(); reservations.deleteAll(); inventories.deleteAll();
        now=Instant.parse("2030-01-01T00:00:00Z");
        when(clock.instant()).thenAnswer(i->now);
        when(clock.withZone(any())).thenAnswer(i->Clock.fixed(now,i.getArgument(0)));
        PNRS.set(1000000000L); CATALOG_CALLS.set(0); SESSION_ACTIVE.set(true); FORWARDED_AUTH.set(false);
    }
    private String token(String username,String role) {
        return Jwts.builder().subject(username).claim("role",role).claim("sid","session-"+username)
            .claim("email",username+"@example.invalid").expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
    private HttpResponse<byte[]> call(String method,String path,String body,String username,String role,String key,boolean internal) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20));
        if(username!=null) builder.header("Authorization","Bearer "+token(username,role));
        if(key!=null) builder.header("Idempotency-Key",key);
        if(internal) builder.header("X-Service-Token",INTERNAL);
        if(body!=null) builder.header("Content-Type","application/json");
        return http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private String text(HttpResponse<byte[]> response){return new String(response.body(),StandardCharsets.UTF_8);}
    private <T> T field(HttpResponse<byte[]> response,String path){return JsonPath.read(text(response),path);}
    private HttpResponse<byte[]> expect(int status,HttpResponse<byte[]> response){assertEquals(status,response.statusCode(),text(response));return response;}
    private String body(String date,String from,String to,String passengers,int fare) {
        return "{\"trainNumber\":\"12345\",\"fromStation\":\""+from+"\",\"toStation\":\""+to+"\",\"seatClass\":\"SL\",\"dateOfJourney\":\""+date+"\",\"expectedTotalFare\":"+fare+",\"passengers\":"+passengers+"}";
    }
    private HttpResponse<byte[]> book(String date,String from,String to,String passengers,int fare,String key) throws Exception {
        return call("POST","/api/reservations/book",body(date,from,to,passengers,fare),"alice","PASSENGER",key,false);
    }
    private HttpResponse<byte[]> book(String key,int count) throws Exception {
        return expect(201,book("2030-01-02","AAA","CCC","["+String.join(",",Collections.nCopies(count,PASSENGER))+"]",120*count,key));
    }
    private HttpResponse<byte[]> search(String endpoint,String date,String from,String to,int count) throws Exception {
        String path="/api/reservations/"+endpoint+"?trainNumber=12345&fromStation="+URLEncoder.encode(from,StandardCharsets.UTF_8)
            +"&toStation="+URLEncoder.encode(to,StandardCharsets.UTF_8)+"&seatClass=SL&dateOfJourney="+date+"&passengers="+count;
        return call("GET",path,null,null,null,null,false);
    }
    private HttpResponse<byte[]> pay(String pnr,String payment,int amount) throws Exception {
        return expect(200,call("POST","/internal/reservations/"+pnr+"/payment-confirmation",
            "{\"razorpayOrderId\":\"order_test\",\"razorpayPaymentId\":\""+payment+"\",\"amount\":"+amount+",\"currency\":\"INR\"}",null,null,null,true));
    }

    @Test void bookingOverviewCountsAllRecordsButNextExcludesPastAndInactiveAndOtherOwners() throws Exception {
        seedOverview("1111111111","alice","CONFIRMED",now.minusSeconds(60));
        seedOverview("2222222222","alice","CANCELLED",now.plusSeconds(60));
        seedOverview("3333333333","alice","PENDING_PAYMENT",now.plusSeconds(120));
        seedOverview("4444444444","alice","WAITING",now.plusSeconds(300));
        seedOverview("5555555555","alice","CONFIRMED",now.plusSeconds(600));
        seedOverview("6666666666","bob","CONFIRMED",now.plusSeconds(180));
        var response=expect(200,call("GET","/api/reservations/my-bookings/overview",null,"alice","PASSENGER",null,false));
        assertEquals(5,((Number)field(response,"$.totalBookings")).intValue());
        assertEquals(2,((Number)field(response,"$.byStatus.CONFIRMED")).intValue());
        assertEquals("4444444444",field(response,"$.nextJourney.pnr"));
        assertEquals("WAITING",field(response,"$.nextJourney.status"));
        var admin=expect(200,call("GET","/api/reservations/my-bookings/overview",null,"administrator","ADMIN",null,false));
        assertEquals(0,((Number)field(admin,"$.totalBookings")).intValue());assertNull(field(admin,"$.nextJourney"));
        expect(401,call("GET","/api/reservations/my-bookings/overview",null,null,null,null,false));
    }
    private void seedOverview(String pnr,String owner,String status,Instant departure){
        reservations.save(com.railway.reservationservice.entity.Reservation.builder().pnr(pnr).username(owner).trainNumber("12345")
            .dateOfJourney(departure.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()).bookingTime(LocalDateTime.of(2030,1,1,0,0))
            .status(com.railway.reservationservice.entity.Reservation.ReservationStatus.valueOf(status)).departureAt(departure).build());
    }
    @Test void assistantBookingLookupNeverGrantsAdminAccessToAnotherOwner() throws Exception {
        String pnr=field(book("chat-owner",1),"$.pnr");
        String path="/api/reservations/my-bookings/"+pnr;
        expect(200,call("GET",path,null,"alice","PASSENGER",null,false));
        expect(403,call("GET",path,null,"bob","PASSENGER",null,false));
        expect(403,call("GET",path,null,"administrator","ADMIN",null,false));
        expect(401,call("GET",path,null,null,null,null,false));
    }

    @ParameterizedTest @ValueSource(strings={"[null]","[null,%s]","[%s,null]"})
    void nullPassengerIsAFieldErrorWithNoDatabaseOrRemoteSideEffects(String template) throws Exception {
        String passengers=template.replace("%s",PASSENGER);
        var response=expect(400,book("2030-01-02","AAA","CCC",passengers,120,"null-entry"));
        assertTrue(text(response).contains("Passenger must not be null"));
        Map<String,Object> fields=field(response,"$.fieldErrors"); assertFalse(fields.isEmpty());
        assertTrue(response.headers().firstValue("X-Request-ID").isPresent());
        assertEquals(0,reservations.count()); assertEquals(0,inventories.count()); assertEquals(0,notifications.count()); assertEquals(0,CATALOG_CALLS.get());
    }
    @ParameterizedTest @ValueSource(strings={"WINDOW","lower",""," LOWER "})
    void unknownBerthPreferenceIsRejected(String preference) throws Exception {
        expect(400,book("2030-01-02","AAA","CCC","["+PASSENGER.replace("LOWER",preference)+"]",120,"bad-berth"));
        assertEquals(0,reservations.count()); assertEquals(0,CATALOG_CALLS.get());
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"LOWER","MIDDLE","UPPER","SIDE_LOWER","SIDE_UPPER","NONE"})
    void allSupportedPreferencesAndNullCanBeBooked(String preference) throws Exception {
        String passenger=PASSENGER.replace("\"LOWER\"",preference==null?"null":"\""+preference+"\"");
        var response=expect(201,book("2030-01-02","AAA","CCC","["+passenger+"]",120,"valid-berth"));
        assertEquals(preference,field(response,"$.passengers[0].birthPreference")); assertEquals(1,reservations.count());
    }
    @Test void stationNormalizationWorksAcrossSearchQuoteBookingAndIdempotentRetry() throws Exception {
        var results=expect(200,search("search","2030-01-02"," aaa "," cCc ",1));
        assertEquals("AAA",field(results,"$[0].fromStation"));
        var quote=expect(200,search("quote","2030-01-02"," aaa "," cCc ",1));
        assertEquals("CCC",field(quote,"$.toStation"));
        var first=expect(201,book("2030-01-02"," aaa "," cCc ","["+PASSENGER+"]",120,"normalized-key"));
        var replay=expect(201,book("2030-01-02","AAA","CCC","["+PASSENGER+"]",120,"normalized-key"));
        assertEquals((String)field(first,"$.pnr"),(String)field(replay,"$.pnr")); assertEquals(1,reservations.count());
        assertEquals("AAA",reservations.findAll().getFirst().getFromStation()); assertTrue(FORWARDED_AUTH.get());
    }
    @ParameterizedTest @ValueSource(strings={"search","quote","book"})
    void sameStationAfterNormalizationIsRejected(String endpoint) throws Exception {
        var response=endpoint.equals("book")?book("2030-01-02"," aaa ","AAA","["+PASSENGER+"]",120,"same-station")
            :search(endpoint,"2030-01-02"," aaa ","AAA",1);
        expect(400,response); assertTrue(text(response).contains("Choose different")); assertEquals(0,CATALOG_CALLS.get()); assertEquals(0,reservations.count());
    }
    static Stream<Arguments> windows() {
        return Stream.of(Arguments.of("2030-01-31T00:00:00Z","2030-03-31","2030-04-01"),
            Arguments.of("2027-12-31T00:00:00Z","2028-02-29","2028-03-01"),
            Arguments.of("2028-12-31T00:00:00Z","2029-02-28","2029-03-01"),
            Arguments.of("2029-12-31T18:30:00Z","2030-03-01","2030-03-02"));
    }
    @ParameterizedTest @MethodSource("windows")
    void calendarWindowHandlesMonthEndsLeapYearsAndIndiaMidnight(String instant,String last,String next) throws Exception {
        now=Instant.parse(instant);
        expect(200,search("search",last,"AAA","CCC",1)); expect(200,search("quote",last,"AAA","CCC",1));
        expect(201,book(last,"AAA","CCC","["+PASSENGER+"]",120,"last-valid-day"));
        expect(400,search("search",next,"AAA","CCC",1)); expect(400,search("quote",next,"AAA","CCC",1));
        var invalid=expect(400,book(next,"AAA","CCC","["+PASSENGER+"]",120,"outside-window"));
        assertTrue(text(invalid).contains("two calendar months")); assertEquals(1,reservations.count());
    }
    @Test void pastDateAndTodayDepartureAreCheckedSeparately() throws Exception {
        expect(400,search("quote","2029-12-31","AAA","CCC",1));
        expect(400,book("2029-12-31","AAA","CCC","["+PASSENGER+"]",120,"past-booking"));
        expect(200,search("quote","2030-01-01","AAA","CCC",1));
        now=Instant.parse("2030-01-01T04:30:00Z"); // Exactly 10:00 India time.
        expect(409,book("2030-01-01","AAA","CCC","["+PASSENGER+"]",120,"departed-booking"));
        assertEquals(0,reservations.count());
    }
    @ParameterizedTest @ValueSource(ints={-2,0,7})
    void invalidPassengerCountsFailBeforeRemoteCalls(int count) throws Exception {
        expect(400,search("search","2030-01-02","AAA","CCC",count));
        expect(400,search("quote","2030-01-02","AAA","CCC",count)); assertEquals(0,CATALOG_CALLS.get());
    }
    @Test void completePaidWaitlistPromotionCancellationRefundAndPdfFlow() throws Exception {
        String confirmed=field(book("full-train",2),"$.pnr");
        assertEquals("CONFIRMED",field(pay(confirmed,"pay_first",240),"$.status"));
        String waiting=field(book("waiting-ticket",1),"$.pnr");
        assertEquals("WAITING",field(pay(waiting,"pay_waiting",120),"$.status"));
        var history=expect(200,call("GET","/api/reservations/my-bookings?status=WL&fromDate=2030-01-02&toDate=2030-01-02",null,"alice","PASSENGER",null,false));
        assertEquals(1,((Number)field(history,"$.totalElements")).intValue());
        var cancellation=expect(200,call("DELETE","/api/reservations/"+confirmed,null,"alice","PASSENGER",null,false));
        assertEquals(192,((Number)field(cancellation,"$.refundAmount")).intValue());
        expect(200,call("DELETE","/api/reservations/"+confirmed,null,"alice","PASSENGER",null,false));
        var promoted=expect(200,call("GET","/api/reservations/pnr/"+waiting,null,"alice","PASSENGER",null,false));
        assertEquals("CONFIRMED",field(promoted,"$.status")); assertNotNull(field(promoted,"$.passengers[0].seatNumber"));
        assertTrue(notifications.existsById(waiting+":PROMOTED"));
        expect(200,call("POST","/internal/reservations/"+confirmed+"/refund-status",
            "{\"razorpayPaymentId\":\"pay_first\",\"refundStatus\":\"PROCESSED\",\"refundAmount\":192}",null,null,null,true));
        assertTrue(notifications.existsById(confirmed+":REFUND_COMPLETED"));
        var pdf=expect(200,call("GET","/api/reservations/"+waiting+"/ticket.pdf",null,"alice","PASSENGER",null,false));
        assertTrue(text(pdf).startsWith("%PDF-")); assertTrue(pdf.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        expect(403,call("GET","/api/reservations/"+waiting+"/ticket.pdf",null,"bob","PASSENGER",null,false));
        expect(200,call("GET","/api/reservations/"+waiting+"/ticket.pdf",null,"admin","ADMIN",null,false));
    }
    @Test void expiryLatePaymentAndWaitlistCutoffAreRecoveredThroughHttpReads() throws Exception {
        String expired=field(book("expire-checkout",2),"$.pnr");
        now=now.plusSeconds(120);
        assertEquals("EXPIRED",field(expect(200,call("GET","/api/reservations/pnr/"+expired,null,"alice","PASSENGER",null,false)),"$.status"));
        assertEquals(Boolean.TRUE,field(pay(expired,"pay_late",240),"$.refundRequired"));
        String cnf=field(book("new-confirmed",2),"$.pnr"); pay(cnf,"pay_cnf",240);
        String wl=field(book("cutoff-waiting",1),"$.pnr"); pay(wl,"pay_wl",120);
        now=Instant.parse("2030-01-02T03:30:00Z");
        var result=expect(200,call("GET","/api/reservations/pnr/"+wl,null,"alice","PASSENGER",null,false));
        assertEquals("CANCELLED",field(result,"$.status")); assertEquals(120,((Number)field(result,"$.refundAmount")).intValue());
        assertTrue(notifications.existsById(wl+":WAITLIST_EXPIRED"));
    }
    @Test void notificationInboxIsPrivatePaginatedAndReadStatePersists() throws Exception {
        String pnr=field(book("notification-owner",1),"$.pnr");
        pay(pnr,"pay_notifications",120);
        String base="/api/reservations/notifications";
        expect(401,call("GET",base,null,null,null,null,false));
        var first=expect(200,call("GET",base+"?size=1",null,"alice","PASSENGER",null,false));
        assertEquals(2,((Number)field(first,"$.unreadCount")).intValue());
        assertEquals(2,((Number)field(first,"$.notifications.totalPages")).intValue());
        assertFalse(text(first).contains("userEmail")); assertFalse(text(first).contains("bookingJson"));
        String id=field(first,"$.notifications.content[0].id");
        for(String role:List.of("PASSENGER","ADMIN")) {
            var other=expect(200,call("GET",base,null,"bob",role,null,false));
            assertEquals(0,((Number)field(other,"$.unreadCount")).intValue());
            expect(404,call("PUT",base+"/"+id+"/read","{}","bob",role,null,false));
            expect(204,call("PUT",base+"/read-all","{}","bob",role,null,false));
        }
        expect(204,call("PUT",base+"/"+id+"/read","{}","alice","PASSENGER",null,false));
        var readAt=inbox.findById(id).orElseThrow().getReadAt();assertNotNull(readAt);
        now=now.plusSeconds(2);
        expect(204,call("PUT",base+"/"+id+"/read","{}","alice","PASSENGER",null,false));
        assertEquals(readAt,inbox.findById(id).orElseThrow().getReadAt());
        var unread=expect(200,call("GET",base+"?unreadOnly=true",null,"alice","PASSENGER",null,false));
        assertEquals(1,((Number)field(unread,"$.notifications.totalElements")).intValue());
        expect(204,call("PUT",base+"/read-all","{}","alice","PASSENGER",null,false));
        assertEquals(0,inbox.countByUsernameAndReadAtIsNull("alice"));
        expect(200,call("DELETE","/api/reservations/"+pnr,null,"alice","PASSENGER",null,false));
        assertEquals(2,inbox.countByUsernameAndReadAtIsNull("alice"));
        expect(400,call("GET",base+"?page=-1",null,"alice","PASSENGER",null,false));
        expect(400,call("GET",base+"?size=101",null,"alice","PASSENGER",null,false));
        expect(404,call("PUT",base+"/missing/read","{}","alice","PASSENGER",null,false));
        SESSION_ACTIVE.set(false);
        expect(401,call("GET",base,null,"alice","PASSENGER",null,false));
    }
    @Test void publicPrivateAndInternalSecurityBoundariesRemainEnforced() throws Exception {
        expect(401,call("GET","/api/reservations/my-bookings",null,null,null,null,false));
        String pnr=field(book("security-check",1),"$.pnr");
        expect(403,call("GET","/api/reservations/pnr/"+pnr,null,"bob","PASSENGER",null,false));
        expect(403,call("GET","/internal/reservations/"+pnr+"/payment-context",null,null,null,null,false));
        expect(409,call("GET","/api/reservations/"+pnr+"/ticket.pdf",null,"alice","PASSENGER",null,false));
        SESSION_ACTIVE.set(false);
        expect(401,call("GET","/api/reservations/my-bookings",null,"alice","PASSENGER",null,false));
    }
}

