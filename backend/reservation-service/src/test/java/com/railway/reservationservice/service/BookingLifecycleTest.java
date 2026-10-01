package com.railway.reservationservice.service;

import com.railway.reservationservice.client.*;
import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.exception.BookingException;
import com.railway.reservationservice.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class BookingLifecycleTest {
    @Autowired ReservationService service;
    @Autowired BookingEngine engine;
    @Autowired NotificationCenter center;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired ReservationRepository reservations;
    @Autowired SeatInventoryRepository inventories;
    @Autowired BookingNotificationRepository notifications;
    @Autowired UserNotificationRepository inbox;
    @MockitoBean PnrServiceClient pnrs;
    @MockitoBean TrainCatalogClient trains;
    @MockitoBean ScheduleCatalogClient schedules;
    @MockitoBean Clock clock;
    private final Instant now=Instant.parse("2030-01-01T00:00:00Z");
    private final LocalDate date=LocalDate.of(2030,1,2);

    @BeforeEach void setup() {
        inbox.deleteAll(); notifications.deleteAll(); reservations.deleteAll(); inventories.deleteAll();
        when(clock.instant()).thenReturn(now);
        when(clock.withZone(any())).thenAnswer(i->Clock.fixed(clock.instant(),i.getArgument(0)));
        AtomicLong sequence=new AtomicLong(1000000000L);
        when(pnrs.generatePnr()).thenAnswer(i->Long.toString(sequence.incrementAndGet()));
        CatalogTrain train=new CatalogTrain(); train.setTrainNumber("12345"); train.setTrainName("Test Express");
        train.setOperatingDays(EnumSet.allOf(DayOfWeek.class)); train.setSeatClasses(Set.of("SL"));
        var config=new CatalogTrain.ClassConfiguration(); config.setSeatClass("SL"); config.setCoachCount(1); config.setSeatsPerCoach(2);
        config.setBaseFare(new BigDecimal("20.00")); config.setRatePerKm(new BigDecimal("0.50")); config.setMinimumFare(new BigDecimal("40.00"));
        train.setClassConfigurations(List.of(config));
        when(trains.getTrain("12345")).thenReturn(train); when(trains.getTrains()).thenReturn(List.of(train));
        when(schedules.getSchedule("12345")).thenReturn(List.of(stop("AAA",1,0,10,0),stop("BBB",2,100,11,0),stop("CCC",3,200,12,0)));
    }
    private CatalogStop stop(String code,int seq,int km,int hour,int dayOffset) {
        CatalogStop stop=new CatalogStop(); stop.setStationCode(code); stop.setStopSequence(seq); stop.setDistanceKm(km);
        stop.setArrivalTime(LocalTime.of(hour,0)); stop.setDepartureTime(LocalTime.of(hour,5)); stop.setDayNumber(1+dayOffset); return stop;
    }
    private ReservationReq request(String from,String to,int count) {
        ReservationReq req=new ReservationReq(); req.setTrainNumber("12345"); req.setFromStation(from); req.setToStation(to); req.setSeatClass("SL"); req.setDateOfJourney(date);
        req.setPassengers(java.util.stream.IntStream.range(0,count).mapToObj(i->PassengerDto.builder().name("Passenger "+i).age(25).gender("OTHER").build()).toList());
        req.setExpectedTotalFare(service.quote("12345",from,to,date,"SL",count).getTotalFare()); return req;
    }
    private ReservationRes book(String key,int count) { return service.bookTicket("alice","test@example.invalid",key,request("AAA","CCC",count)); }
    private PaymentConfirmation paid(ReservationRes booking,String id) { return new PaymentConfirmation("order_test",id,booking.getTotalFare(),"INR"); }

    @Test void pendingBookingUsesServerFareAndTwoMinuteHold() {
        ReservationRes r=book("request_1",2);
        assertEquals("PENDING_PAYMENT",r.getStatus()); assertEquals(now.plusSeconds(120),r.getHoldExpiresAt());
        assertEquals(new BigDecimal("240.00"),r.getTotalFare()); assertEquals(2,r.getPassengers().stream().map(PassengerDto::getSeatNumber).distinct().count());
        assertEquals("SL-C1-1",r.getPassengers().getFirst().getSeatNumber());
        assertEquals("S1",r.getPassengers().getFirst().getSeatDetails().coach());
        assertEquals("LOWER",r.getPassengers().getFirst().getSeatDetails().berthType());
        assertEquals("MIDDLE",r.getPassengers().get(1).getSeatDetails().berthType());
        assertEquals(0,notifications.count()); assertEquals(0,service.quote("12345","AAA","CCC",date,"SL",1).getAvailableSeats());
    }
    @Test void rejectsFareTamperingAndIdempotencyKeyReuseWithDifferentPassengers() {
        ReservationReq req=request("AAA","CCC",1); req.setExpectedTotalFare(BigDecimal.ONE);
        assertThrows(BookingException.class,()->service.bookTicket("alice",null,"tampered_1",req)); assertEquals(0,reservations.count());
        ReservationRes first=book("same_key",1); assertEquals(first.getPnr(),book("same_key",1).getPnr());
        ReservationReq changed=request("AAA","CCC",1); changed.getPassengers().getFirst().setName("Different");
        assertThrows(BookingException.class,()->service.bookTicket("alice",null,"same_key",changed)); assertEquals(1,reservations.count());
    }
    @Test void simultaneousBookingsCannotOversell() throws Exception {
        ReservationReq req=request("AAA","CCC",1);
        try(ExecutorService pool=Executors.newFixedThreadPool(3)) {
            CountDownLatch start=new CountDownLatch(1); List<Future<Boolean>> work=new ArrayList<>();
            for(int i=0;i<3;i++) { final int n=i; work.add(pool.submit(()-> { start.await(); try { service.bookTicket("alice",null,"concurrent_"+n,req); return true; } catch(BookingException ex) { return false; } })); }
            start.countDown(); int success=0; for(Future<Boolean> f:work) if(f.get(20,TimeUnit.SECONDS)) success++;
            assertEquals(3,success); assertEquals(3,reservations.count());
            assertEquals(2,service.getMyBookings("alice").stream().flatMap(r->r.getPassengers().stream()).map(PassengerDto::getSeatNumber).filter(Objects::nonNull).distinct().count());
        }
    }
    @Test void seatsCanBeReusedOnNonOverlappingRouteSegments() {
        var one=service.bookTicket("alice",null,"segment_1",request("AAA","BBB",2));
        var two=service.bookTicket("alice",null,"segment_2",request("BBB","CCC",2));
        assertEquals(one.getPassengers().getFirst().getSeatNumber(),two.getPassengers().getFirst().getSeatNumber());
        assertEquals("WL",book("overlapping",1).getAllocation());
    }
    @Test void captureConfirmsOnceAndCreatesNotificationOnlyThen() {
        var r=book("confirm_1",1); var event=paid(r,"pay_first");
        assertFalse(engine.confirm(r.getPnr(),event).refundRequired()); assertFalse(engine.confirm(r.getPnr(),event).refundRequired());
        assertEquals("CONFIRMED",service.getReservationByPnr(r.getPnr(),"alice",false).getStatus()); assertEquals(1,notifications.count());
        var duplicate=engine.confirm(r.getPnr(),paid(r,"pay_extra")); assertTrue(duplicate.refundRequired()); assertEquals(r.getTotalFare(),duplicate.refundAmount());
        assertEquals("pay_first",engine.paymentContext(r.getPnr()).razorpayPaymentId());
    }
    @Test void expiredHoldReleasesSeatsAndLatePaymentIsFullyRefundable() {
        var r=book("expiring_1",2); when(clock.instant()).thenReturn(now.plusSeconds(120));
        engine.expireHolds(); assertEquals("EXPIRED",service.getReservationByPnr(r.getPnr(),"alice",false).getStatus());
        assertEquals(2,service.quote("12345","AAA","CCC",date,"SL",1).getAvailableSeats());
        var late=engine.confirm(r.getPnr(),paid(r,"pay_late")); assertTrue(late.refundRequired()); assertEquals(r.getTotalFare(),late.refundAmount());
        assertEquals("PENDING",engine.paymentContext(r.getPnr()).refundStatus()); assertEquals(2,notifications.count());
        assertTrue(notifications.existsById(r.getPnr()+":PAYMENT_INCOMPLETE"));
        assertTrue(notifications.existsById(r.getPnr()+":REFUND_INITIATED"));
    }
    @Test void cancellationEnforcesOwnershipAndTracksEightyPercentRefund() {
        var r=book("cancel_1",1); engine.confirm(r.getPnr(),paid(r,"pay_cancel"));
        assertThrows(BookingException.class,()->service.getReservationByPnr(r.getPnr(),"bob",false));
        assertThrows(BookingException.class,()->service.cancelReservation(r.getPnr(),"bob",false));
        var cancelled=service.cancelReservation(r.getPnr(),"alice",false);
        assertEquals(new BigDecimal("24.00"),cancelled.getCancellationFee()); assertEquals(new BigDecimal("96.00"),cancelled.getRefundAmount());
        assertEquals("PENDING",cancelled.getRefundStatus()); assertEquals("CANCELLED",service.getReservationByPnr(r.getPnr(),"alice",false).getPassengers().getFirst().getBookingStatus());
        engine.refundStatus(r.getPnr(),new RefundUpdate("pay_cancel","PROCESSED",cancelled.getRefundAmount()));
        engine.refundStatus(r.getPnr(),new RefundUpdate("pay_cancel","PENDING",cancelled.getRefundAmount()));
        assertEquals("PROCESSED",engine.paymentContext(r.getPnr()).refundStatus());
        assertEquals(cancelled.getRefundAmount(),service.cancelReservation(r.getPnr(),"alice",false).getRefundAmount());
    }
    @Test void pendingCancellationHasNoChargeButLateCaptureReturnsFullAmount() {
        var r=book("cancel_unpaid",1); var c=service.cancelReservation(r.getPnr(),"alice",false);
        assertEquals(0,c.getCancellationFee().signum()); assertEquals(0,c.getRefundAmount().signum());
        assertEquals(r.getTotalFare(),engine.confirm(r.getPnr(),paid(r,"pay_after_cancel")).refundAmount());
    }
    @Test void overnightBoardingResolvesTrainRunDateCorrectly() {
        CatalogStop a=stop("AAA",1,0,23,0), b=stop("BBB",2,100,0,1), c=stop("CCC",3,200,2,1);
        b.setDepartureDayNumber(2); when(schedules.getSchedule("12345")).thenReturn(List.of(a,b,c));
        var q=service.quote("12345","BBB","CCC",date,"SL",1);
        assertEquals(date.minusDays(1),q.getRunDate()); assertEquals(new BigDecimal("70.00"),q.getTotalFare());
        assertThrows(BookingException.class,()->service.quote("12345","CCC","AAA",date,"SL",1));
    }
    @Test void paidWaitlistPromotesWholeBookingWhenSeatsAreReleased() {
        var first=book("occupy_all",2); engine.confirm(first.getPnr(),paid(first,"pay_occupy"));
        var wl=book("wait_group",2); assertEquals("WL",wl.getAllocation()); assertTrue(wl.getPassengers().stream().allMatch(p->p.getSeatNumber()==null));
        assertTrue(wl.getPassengers().stream().allMatch(p->p.getSeatDetails().coach()==null));
        assertEquals("WAITING",engine.confirm(wl.getPnr(),paid(wl,"pay_wait")).status());
        assertEquals(1,engine.get(wl.getPnr(),"alice",false).getWaitlistPosition());
        engine.cancel(first.getPnr(),"alice",false);
        var promoted=engine.get(wl.getPnr(),"alice",false); assertEquals("CONFIRMED",promoted.getStatus());
        assertEquals("S1",promoted.getPassengers().getFirst().getSeatDetails().coach());
        assertEquals("LOWER",promoted.getPassengers().getFirst().getSeatDetails().berthType());
        assertEquals(2,promoted.getPassengers().stream().map(PassengerDto::getSeatNumber).distinct().count());
        assertTrue(notifications.existsById(wl.getPnr()+":PROMOTED"));
        engine.confirm(wl.getPnr(),paid(wl,"pay_wait"));
        assertEquals(1,notifications.findAll().stream().filter(e->e.getEventId().equals(wl.getPnr()+":PROMOTED")).count());
    }
    @Test void smallerBookingsCannotJumpThePaidQueueHead() {
        var one=book("first_seat",1); engine.confirm(one.getPnr(),paid(one,"pay_one"));
        var large=book("large_wait",2); engine.confirm(large.getPnr(),paid(large,"pay_large"));
        var small=book("small_wait",1); assertEquals("WL",small.getAllocation()); engine.confirm(small.getPnr(),paid(small,"pay_small"));
        assertEquals("WAITING",engine.get(large.getPnr(),"alice",false).getStatus());
        assertEquals(2,engine.get(small.getPnr(),"alice",false).getWaitlistPosition());
        engine.cancel(one.getPnr(),"alice",false);
        assertEquals("CONFIRMED",engine.get(large.getPnr(),"alice",false).getStatus());
        assertEquals("WAITING",engine.get(small.getPnr(),"alice",false).getStatus());
    }
    @Test void cutoffCancelsWaitingBookingAndRequestsFullRefundExactlyOnce() {
        var first=book("cutoff_hold",2);engine.confirm(first.getPnr(),paid(first,"pay_full"));
        var wl=book("cutoff_wait",1);engine.confirm(wl.getPnr(),paid(wl,"pay_wl"));
        when(clock.instant()).thenReturn(wl.getWaitlistDeadline());engine.expireHolds();engine.expireHolds();
        var current=engine.get(wl.getPnr(),"alice",false);assertEquals("CANCELLED",current.getStatus());assertEquals(wl.getTotalFare(),current.getRefundAmount());
        assertEquals("PENDING",current.getRefundStatus());assertTrue(notifications.existsById(wl.getPnr()+":WAITLIST_EXPIRED"));
        assertEquals("CONFIRMED",engine.get(first.getPnr(),"alice",false).getStatus());
        assertTrue(engine.confirm(wl.getPnr(),paid(wl,"pay_wl")).refundRequired());
        assertThrows(BookingException.class,()->book("after_cutoff",1));
    }
    @Test void unpaidWaitlistNeverHoldsAQueuePlaceAndAnImpossibleGroupIsRejected() {
        var first=book("hold_expiry",2);var wl=book("unpaid_wait",1);
        when(clock.instant()).thenReturn(now.plusSeconds(120));engine.expireHolds();
        assertEquals("EXPIRED",engine.get(wl.getPnr(),"alice",false).getStatus());
        assertEquals("CNF",book("new_booking",1).getAllocation());
        assertThrows(BookingException.class,()->book("too_large",3));
    }
    @Test void waitlistCancellationPreviewIsFreeAndDoesNotCancel() {
        var first=book("preview_hold",2);engine.confirm(first.getPnr(),paid(first,"pay_p1"));
        var wl=book("preview_wait",1);engine.confirm(wl.getPnr(),paid(wl,"pay_p2"));
        var preview=engine.preview(wl.getPnr(),"alice",false);assertEquals(0,preview.getCancellationFee().signum());assertEquals(wl.getTotalFare(),preview.getRefundAmount());
        assertEquals("WAITING",engine.get(wl.getPnr(),"alice",false).getStatus());
        assertEquals(wl.getTotalFare(),engine.cancel(wl.getPnr(),"alice",false).getRefundAmount());
    }
    @Test void cancellationAndNewCheckoutCannotStealSeatsFromPaidWaitlist() throws Exception {
        var full=book("race_occupied",2);engine.confirm(full.getPnr(),paid(full,"pay_race_full"));
        var wl=book("race_waiting",2);engine.confirm(wl.getPnr(),paid(wl,"pay_race_wait"));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var cancel=pool.submit(()->{start.await();return engine.cancel(full.getPnr(),"alice",false);});
            var newcomer=pool.submit(()->{start.await();return book("race_newcomer",1);});
            start.countDown();cancel.get(20,TimeUnit.SECONDS);var next=newcomer.get(20,TimeUnit.SECONDS);
            assertEquals("WL",next.getAllocation());
            assertEquals("CONFIRMED",engine.get(wl.getPnr(),"alice",false).getStatus());
            assertEquals(0,service.quote("12345","AAA","CCC",date,"SL",1).getAvailableSeats());
        }
    }
    @Test void historyFiltersAndPaginationAreOwnerScoped() {
        var one=book("history_one",1);engine.confirm(one.getPnr(),paid(one,"pay_history"));book("history_two",1);
        var page=service.history("alice",0,1,"bookingTime","desc","CNF",date,date);
        assertEquals(1,page.totalElements());assertEquals(one.getPnr(),page.content().getFirst().getPnr());
        assertTrue(service.history("bob",0,20,"bookingTime","desc",null,null,null).content().isEmpty());
        assertThrows(IllegalArgumentException.class,()->service.history("alice",0,101,"bookingTime","desc",null,null,null));
        assertThrows(BookingException.class,()->service.history("alice",0,20,"bookingTime","desc",null,date.plusDays(1),date));
    }

    @Test void inboxCommitsWithBookingAndDeduplicatesRepeatedProviderUpdates() {
        var b=book("inapp_hold",1);
        assertTrue(inbox.existsById(b.getPnr()+":BOOKING_HELD"));
        engine.paymentFailed(b.getPnr());engine.paymentFailed(b.getPnr());
        assertEquals(2,inbox.count());
        engine.confirm(b.getPnr(),paid(b,"pay_inapp"));engine.confirm(b.getPnr(),paid(b,"pay_inapp"));
        assertEquals(3,inbox.count());
        var item=inbox.findById(b.getPnr()+":PAYMENT_RECEIVED").orElseThrow();
        assertEquals("Your ticket is confirmed",item.getTitle());assertEquals(now,item.getCreatedAt());
        assertTrue(item.getMessage().contains("AAA → CCC"));
        center.read("alice",item.getEventId());
        engine.cancel(b.getPnr(),"alice",false);
        assertNotNull(inbox.findById(item.getEventId()).orElseThrow().getReadAt());
        assertEquals(4,center.list("alice",0,20,true).unreadCount());
    }
    @Test void rolledBackTransitionDoesNotPublishAnInboxEntryAndMissingEmailStillGetsInAppUpdate() {
        var booking=com.railway.reservationservice.entity.Reservation.builder().pnr("rollback-inbox").username("alice")
            .fromStation("AAA").toStation("CCC").trainNumber("12345").dateOfJourney(date).build();
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class,()->tx.execute(status->{center.publish(booking,"CANCELLED","Cancelled");throw new IllegalStateException("rollback");}));
        assertEquals(0,inbox.count());
        tx.executeWithoutResult(status->center.publish(booking,"CANCELLED","Cancelled"));
        assertEquals(1,inbox.count());assertEquals(0,notifications.count());
    }
    @Test void emailSnapshotsRetainConfirmedSeatsAfterCancellationAndIncludeFees() {
        var b=book("email_snapshot",1);engine.confirm(b.getPnr(),paid(b,"pay_mail"));
        var json=new tools.jackson.databind.json.JsonMapper();
        String snapshot=notifications.findById(b.getPnr()+":PAYMENT_RECEIVED").orElseThrow().getBookingJson();
        assertTrue(json.readTree(snapshot).path("departureAt").isString());
        var saved=json.readValue(snapshot,ReservationRes.class);
        assertEquals("CONFIRMED",saved.getStatus());assertEquals("Test Express",saved.getTrainName());assertNotNull(saved.getDepartureAt());assertNotNull(saved.getPassengers().getFirst().getSeatNumber());
        engine.cancel(b.getPnr(),"alice",false);
        assertEquals(snapshot,notifications.findById(b.getPnr()+":PAYMENT_RECEIVED").orElseThrow().getBookingJson());
        var cancelled=json.readValue(notifications.findById(b.getPnr()+":CANCELLED").orElseThrow().getBookingJson(),ReservationRes.class);
        assertEquals(new BigDecimal("24.00"),cancelled.getCancellationFee());assertEquals(new BigDecimal("96.00"),cancelled.getRefundAmount());
        var bytes=new TicketPdfService(engine).render(saved);assertTrue(new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF"));
    }
    @Test void failedPaymentEmailsDeduplicateAndNeverOverridePaidBooking() {
        var b=book("email_failure",1);engine.paymentFailed(b.getPnr());engine.paymentFailed(b.getPnr());
        assertEquals(1,notifications.count());assertEquals("PENDING_PAYMENT",engine.get(b.getPnr(),"alice",false).getStatus());
        engine.confirm(b.getPnr(),paid(b,"pay_retried"));long count=notifications.count();engine.paymentFailed(b.getPnr());assertEquals(count,notifications.count());
        var next=book("email_expiry",1);when(clock.instant()).thenReturn(now.plusSeconds(121));engine.expireHolds();
        assertTrue(notifications.existsById(next.getPnr()+":PAYMENT_INCOMPLETE"));
    }
}

