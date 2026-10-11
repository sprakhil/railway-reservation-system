package com.railway.reservationservice.service;
import com.railway.reservationservice.client.*;
import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.repository.*;
import com.railway.reservationservice.exception.BookingException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.*;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:run-cancel;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000") @AutoConfigureMockMvc
class TrainRunCancellationTest {
 @Autowired ReservationService bookings; @Autowired BookingEngine engine; @Autowired RunCancellationService runs;
 @Autowired ReservationRepository reservations; @Autowired TrainRunCancellationRepository cancelled;
 @Autowired SeatInventoryRepository inventories; @Autowired BookingNotificationRepository outbox; @Autowired UserNotificationRepository inbox;
 @Autowired MockMvc mvc; @Autowired TicketPdfService pdf;
 @MockitoBean TrainCatalogClient trains; @MockitoBean ScheduleCatalogClient schedules; @MockitoBean PnrServiceClient pnrs; @MockitoBean Clock clock;
 final Instant now=Instant.parse("2030-01-01T00:00:00Z");final LocalDate date=LocalDate.of(2030,1,2);CatalogTrain train;
 @BeforeEach void setup(){inbox.deleteAll();outbox.deleteAll();reservations.deleteAll();cancelled.deleteAll();inventories.deleteAll();
  when(clock.instant()).thenReturn(now);when(clock.withZone(any())).thenAnswer(i->Clock.fixed(clock.instant(),i.getArgument(0)));
  var counter=new AtomicLong(1000000000);when(pnrs.generatePnr()).thenAnswer(i->""+counter.incrementAndGet());
  train=new CatalogTrain();train.setTrainNumber("T1");train.setTrainName("Test train");train.setOperatingDays(EnumSet.allOf(DayOfWeek.class));train.setSeatClasses(Set.of("SL","CLASS_3A"));
  train.setClassConfigurations(List.of(config("SL"),config("CLASS_3A")));when(trains.getTrain("T1")).thenReturn(train);when(trains.getTrains()).thenReturn(List.of(train));
  when(schedules.getSchedule("T1")).thenReturn(List.of(stop("AAA",1,0,1,null,LocalTime.of(22,0)),stop("BBB",2,100,2,LocalTime.of(1,0),LocalTime.of(1,10)),stop("CCC",3,200,2,LocalTime.of(3,0),null)));
 }
 CatalogTrain.ClassConfiguration config(String c){var x=new CatalogTrain.ClassConfiguration();x.setSeatClass(c);x.setCoachCount(1);x.setSeatsPerCoach(2);x.setBaseFare(new BigDecimal("20"));x.setRatePerKm(new BigDecimal("0.5"));x.setMinimumFare(new BigDecimal("40"));return x;}
 CatalogStop stop(String code,int seq,int km,int day,LocalTime a,LocalTime d){var s=new CatalogStop();s.setStationCode(code);s.setStopSequence(seq);s.setDistanceKm(km);s.setDayNumber(day);s.setDepartureDayNumber(day);s.setArrivalTime(a);s.setDepartureTime(d);return s;}
 ReservationReq request(String cls,String from,LocalDate boarding,int n){var r=new ReservationReq();r.setTrainNumber("T1");r.setFromStation(from);r.setToStation("CCC");r.setDateOfJourney(boarding);r.setSeatClass(cls);r.setPassengers(java.util.stream.IntStream.range(0,n).mapToObj(i->PassengerDto.builder().name("Passenger "+i).age(30).gender("OTHER").build()).toList());r.setExpectedTotalFare(bookings.quote("T1",from,"CCC",boarding,cls,n).getTotalFare());return r;}
 ReservationRes book(String key,String cls,String from,LocalDate day,int n){return bookings.bookTicket("alice","alice@example.test",key,request(cls,from,day,n));}
 void pay(ReservationRes r){engine.confirm(r.getPnr(),new PaymentConfirmation("order_"+r.getPnr(),"pay_"+r.getPnr(),r.getTotalFare(),"INR"));}
 ReservationRes get(ReservationRes r){return bookings.getReservationByPnr(r.getPnr(),"alice",false);}

 @Test void fullRunIncludesAllClassesAndNextDayBoardingWithoutPromotion() throws Exception {
  var cnf=book("confirmed","SL","AAA",date,2);pay(cnf);var wl=book("waitlisted","SL","AAA",date,1);pay(wl);
  var overnight=book("nextday1","CLASS_3A","BBB",date.plusDays(1),1);pay(overnight);
  var pending=book("pending01","CLASS_3A","AAA",date,1);
  var tomorrow=book("otherdate","SL","AAA",date.plusDays(1),1);pay(tomorrow);
  var preview=runs.preview("T1",date);assertEquals(4,preview.bookings());assertEquals(5,preview.passengers());assertEquals(2,preview.confirmedBookings());assertEquals(1,preview.waitingBookings());
  var result=runs.cancel("T1",date,"Track maintenance","admin");assertEquals(4,result.bookings());assertEquals(new BigDecimal("430.00"),result.refundAmount());
  for(var b:List.of(cnf,wl,overnight,pending)){var updated=get(b);assertEquals("CANCELLED",updated.getStatus());assertEquals(0,updated.getCancellationFee().signum());assertEquals("Track maintenance",updated.getOperatorCancellationReason());assertTrue(outbox.existsById(b.getPnr()+":TRAIN_CANCELLED"));}
  assertFalse(outbox.existsById(wl.getPnr()+":PROMOTED"));assertEquals("CONFIRMED",get(tomorrow).getStatus());
  assertEquals("NONE",get(pending).getRefundStatus());assertEquals(0,get(pending).getRefundAmount().signum());
  assertTrue(bookings.search("AAA","CCC",date,"SL",1).isEmpty());assertTrue(bookings.search("BBB","CCC",date.plusDays(1),"SL",1).isEmpty());
  try(var doc=org.apache.pdfbox.Loader.loadPDF(pdf.download(cnf.getPnr(),"alice",false))){assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(doc).contains("Track maintenance"));}
 }
 @Test void staleQuoteCannotBookEvenWhenRunHasNoExistingBookings(){var req=request("SL","AAA",date,1);var quote=bookings.quote("T1","AAA","CCC",date,"SL",1);runs.cancel("T1",date,"Track repair","admin");
  assertThrows(BookingException.class,()->bookings.bookTicket("alice",null,"stale_key",req));
  // Exercise final locked allocation independently of quote validation.
  inventories.findById(quote.inventoryKey()).orElseGet(()->{var i=new com.railway.reservationservice.entity.SeatInventory();i.setInventoryKey(quote.inventoryKey());i.setCapacity(2);return inventories.save(i);});
  assertThrows(BookingException.class,()->engine.create("alice",null,"key","hash","1234567890",req,quote));assertEquals(0,reservations.count());}
 @Test void lateCaptureGetsFullRefundAndRepeatedCancellationDoesNotDuplicateEvents(){var b=book("pending01","SL","AAA",date,1);runs.cancel("T1",date,"Track repair","admin");long events=outbox.count();
  runs.cancel("T1",date,"Different retry reason","admin2");assertEquals(events,outbox.count());assertEquals("Track repair",cancelled.findAll().getFirst().getReason());
  var decision=engine.confirm(b.getPnr(),new PaymentConfirmation("order","pay_late",b.getTotalFare(),"INR"));assertTrue(decision.refundRequired());assertEquals(b.getTotalFare(),decision.refundAmount());assertEquals("CANCELLED",get(b).getStatus());
  engine.refundStatus(b.getPnr(),new RefundUpdate("pay_late","PROCESSED",b.getTotalFare()));assertEquals(b.getTotalFare(),runs.preview("T1",date).processedRefunds());}
 @Test void previouslyCancelledTicketsRetainTheirOriginalFee(){var b=book("oldcancel","SL","AAA",date,1);pay(b);bookings.cancelReservation(b.getPnr(),"alice",false);var before=get(b);runs.cancel("T1",date,"Track repair","admin");assertEquals(before.getRefundAmount(),get(b).getRefundAmount());assertNull(get(b).getOperatorCancelledAt());assertEquals(0,runs.preview("T1",date).bookings());}
 @Test void validationAndAdminAuthorization()throws Exception{
  String base="/api/reservations/admin/train-runs/T1/2030-01-02";
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base)).andExpect(status().isUnauthorized());mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base).with(user("alice").roles("USER"))).andExpect(status().isForbidden());
  mvc.perform(post(base+"/cancel").with(user("alice").roles("USER")).contentType("application/json").content("{\"reason\":\"Track repair\"}")).andExpect(status().isForbidden());
  mvc.perform(post(base+"/cancel").with(user("admin").roles("ADMIN")).contentType("application/json").content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base).with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.cancelled").value(false));
  mvc.perform(post(base+"/cancel").with(user("admin").roles("ADMIN")).contentType("application/json").content("{\"reason\":\"Track repair\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.cancelled").value(true));
  assertThrows(BookingException.class,()->runs.cancel("T1",date.plusMonths(3),"Track repair","admin"));
 }
 @Test void rejectsStartedRunsEvenForLaterBoarding(){when(clock.instant()).thenReturn(date.atTime(22,0).atZone(ZoneId.of("Asia/Kolkata")).toInstant());assertThrows(BookingException.class,()->runs.cancel("T1",date,"Track repair","admin"));assertEquals(0,cancelled.count());}
 @Test void unsupportedAffectedRecordRollsBackDecisionBookingsAndNotifications(){var first=book("firstpaid","SL","AAA",date,1);pay(first);var second=book("oldformat","CLASS_3A","AAA",date,1);var legacy=reservations.findByPnr(second.getPnr()).orElseThrow();legacy.setDataVersion(1);reservations.saveAndFlush(legacy);
  assertThrows(BookingException.class,()->runs.cancel("T1",date,"Track repair","admin"));assertEquals(0,cancelled.count());assertEquals("CONFIRMED",get(first).getStatus());assertNull(get(first).getOperatorCancelledAt());assertFalse(outbox.existsById(first.getPnr()+":TRAIN_CANCELLED"));assertFalse(inbox.existsById(first.getPnr()+":TRAIN_CANCELLED"));}
 @Test void concurrentPaymentAndCancellationAlwaysEndCancelledWithFullRefund()throws Exception{var b=book("race_paid","SL","AAA",date,1);try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var c=pool.submit(()->{start.await();return runs.cancel("T1",date,"Track repair","admin");});var p=pool.submit(()->{start.await();pay(b);return true;});start.countDown();c.get(20,TimeUnit.SECONDS);p.get(20,TimeUnit.SECONDS);}var result=get(b);assertEquals("CANCELLED",result.getStatus());assertEquals(b.getTotalFare(),result.getRefundAmount());assertEquals(0,result.getCancellationFee().signum());}
 @Test void concurrentNewBookingCannotEscapeRunCancellation()throws Exception{var req=request("SL","AAA",date,1);try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var c=pool.submit(()->{start.await();return runs.cancel("T1",date,"Track repair","admin");});var b=pool.submit(()->{start.await();try{return bookings.bookTicket("alice",null,"race_book",req);}catch(BookingException expected){return null;}});start.countDown();c.get(20,TimeUnit.SECONDS);var booked=b.get(20,TimeUnit.SECONDS);if(booked!=null)assertEquals("CANCELLED",get(booked).getStatus());}assertTrue(reservations.findAll().stream().allMatch(r->r.getStatus()==com.railway.reservationservice.entity.Reservation.ReservationStatus.CANCELLED));}
}
