package com.railway.chatbotservice.service;
import com.railway.chatbotservice.config.SecurityConfig.Identity;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class RailwayToolsTest {
    HttpServer server;RailwayTools tools;Conversations.State state;AtomicInteger calls=new AtomicInteger();AtomicReference<String> path=new AtomicReference<>(),bearer=new AtomicReference<>();int status=200;
    @BeforeEach void setup()throws Exception{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",e->{calls.incrementAndGet();path.set(e.getRequestURI().getPath());bearer.set(e.getRequestHeaders().getFirst("Authorization"));String body=path.get().endsWith("/search")?"[{\"trainNumber\":\"12345\",\"trainName\":\"Coastal Express\",\"totalFare\":600,\"availableSeats\":5,\"suggestedAllocation\":\"CNF\"}]":"{\"pnr\":\"1234567890\",\"trainNumber\":\"12345\",\"status\":\"CNF\",\"refundStatus\":\"NONE\",\"totalFare\":600,\"passengers\":[{\"name\":\"Private Name\"}]}";byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();
        var client=WebClient.builder().baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).build();state=new Conversations.State("alice");tools=new RailwayTools(client,client,new Identity("alice","private-jwt"),state);
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void ownPnrToolForwardsJwtButOnlyShowsPrivateDetailsInLocalCard(){
        String alias=state.alias("1234567890");var result=tools.bookingStatus(alias);
        assertEquals("/api/reservations/my-bookings/1234567890",path.get());assertEquals("Bearer private-jwt",bearer.get());assertEquals("CNF",result.get("status"));
        assertFalse(result.toString().contains("1234567890"));assertFalse(result.toString().contains("Private Name"));assertEquals("1234567890",tools.cards().getFirst().pnr());
    }
    @Test void guessedReferencesAndRemoteDenialsNeverProduceBookingCards(){assertTrue(tools.bookingStatus("booking-999").containsKey("notice"));assertEquals(0,calls.get());status=403;assertTrue(tools.bookingStatus(state.alias("1234567890")).containsKey("notice"));assertTrue(tools.cards().isEmpty());}
    @Test void trainCardsComeFromValidatedLiveSearchAndCallsAreBounded(){
        String date=LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1).toString();tools.searchTrains("AAA","AAA",date,"SL",1);assertEquals(0,calls.get());
        var result=tools.searchTrains("AAA","BBB",date,"SL",2);assertEquals("12345",result.getFirst().get("trainNumber"));assertEquals(600.0,tools.cards().getFirst().fare());assertEquals(2,tools.cards().getFirst().passengers());
        for(int i=0;i<10;i++)tools.searchTrains("AAA","BBB",date,"SL",2);assertEquals(6,calls.get());
    }
    @Test void acClassCodesAndCommonAliasesAreAccepted(){
        String date=LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1).toString();
        for(String name:java.util.List.of("CLASS_3A","3A","CLASS_2S")){
            tools.searchTrains("AAA","BBB",date,name,1);
            assertTrue(tools.cards().getFirst().seatClass().startsWith("CLASS_"));
        }
        assertEquals(3,calls.get());
    }
}
