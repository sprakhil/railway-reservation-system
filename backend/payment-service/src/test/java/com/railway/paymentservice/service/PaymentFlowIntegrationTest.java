package com.railway.paymentservice.service;

import com.railway.paymentservice.client.ReservationClient;
import com.railway.paymentservice.dto.*;
import com.railway.paymentservice.entity.*;
import com.railway.paymentservice.exception.PaymentException;
import com.railway.paymentservice.provider.PaymentProvider;
import com.railway.paymentservice.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real H2 transactions and locks, with external systems replaced by in-process mocks. */
@SpringBootTest(properties = {
    "spring.cloud.config.enabled=false", "spring.cloud.config.import-check.enabled=false",
    "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
    "spring.cloud.compatibility-verifier.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:payments;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "railway.payment.reconciliation-enabled=false",
    "jwt.secret=offline-test-secret-at-least-thirty-two-characters-long"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class PaymentFlowIntegrationTest {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    private static final String PNR = "1234567890";
    private static final BigDecimal FARE = new BigDecimal("125.50");
    private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");
    @Autowired PaymentService service;
    @Autowired PaymentStore store;
    @Autowired PaymentRepository records;
    @Autowired PaymentCaptureRepository captures;
    @Autowired PaymentWebhookRepository webhooks;
    @MockitoBean PaymentProvider provider;
    @MockitoBean ReservationClient reservations;
    @MockitoBean Clock clock;

    @BeforeEach void setup() {
        captures.deleteAll(); records.deleteAll(); webhooks.deleteAll();
        when(clock.instant()).thenReturn(NOW);
        when(reservations.context(PNR)).thenReturn(context("PENDING_PAYMENT", NOW.plusSeconds(300), FARE));
        when(provider.publicKey()).thenReturn("rzp_test_offline");
    }
    private ReservationPaymentContext context(String status, Instant expiry, BigDecimal fare) {
        return new ReservationPaymentContext(PNR, "alice", fare, "INR", status, expiry, null, BigDecimal.ZERO, "NONE");
    }
    private PaymentRecord existingOrder() {
        PaymentRecord r = records.saveAndFlush(PaymentRecord.builder().pnr(PNR).username("alice").amount(FARE)
                .currency("INR").receipt("rail_receipt").razorpayOrderId("order_1")
                .status(PaymentRecord.PaymentStatus.CREATED).holdExpiresAt(NOW.plusSeconds(300))
                .createdAt(NOW).updatedAt(NOW).nextReconcileAt(NOW).build());
        when(provider.fetchOrder("order_1")).thenReturn(new PaymentProvider.Order("order_1",12550,"INR","rail_receipt","paid"));
        return r;
    }
    private PaymentProvider.Payment captured(String id) {
        return new PaymentProvider.Payment(id, "order_1", 12550, "INR", "captured", true, 0);
    }
    private PaymentVerificationReq verification(String id) {
        return new PaymentVerificationReq("order_1", id, "signature");
    }
    private void confirmNormally() {
        when(reservations.confirm(eq(PNR), any())).thenReturn(new PaymentConfirmationResult(PNR,"CONFIRMED",false,BigDecimal.ZERO));
    }
    private void refunds(BigDecimal amount) {
        when(provider.refund(anyString(), anyLong(), anyString())).thenAnswer(i ->
                new PaymentProvider.Refund("rfnd_1", i.getArgument(0), i.getArgument(1), "processed", i.getArgument(2)));
        when(reservations.confirm(eq(PNR), any())).thenReturn(new PaymentConfirmationResult(PNR,"EXPIRED",true,amount));
    }
    private void assertStatus(HttpStatus expected, Runnable call) {
        assertEquals(expected, assertThrows(PaymentException.class, call::run).getStatus());
    }

    @Test void httpCheckoutVerificationAndRetryPersistOneAcceptedWaitlistPayment() throws Exception {
        existingOrder();
        when(provider.fetchPayment("pay_http")).thenReturn(captured("pay_http"));
        when(reservations.confirm(eq(PNR),any())).thenAnswer(i->{
            when(reservations.context(PNR)).thenReturn(context("WAITING",NOW.plusSeconds(300),FARE));
            return new PaymentConfirmationResult(PNR,"WAITING",false,BigDecimal.ZERO);
        });
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payments/create-order")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("alice").roles("PASSENGER"))
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"pnr\":\""+PNR+"\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.amountInPaise").value(12550));
        for(int attempt=0;attempt<2;attempt++) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payments/verify")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("alice").roles("PASSENGER"))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"razorpayOrderId\":\"order_1\",\"razorpayPaymentId\":\"pay_http\",\"razorpaySignature\":\"signature\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.paymentStatus").value("SUCCESS"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.bookingStatus").value("WAITING"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.payments[0].bookingConfirmed").value(false));
        }
        assertEquals(1,records.count()); assertEquals(1,captures.count());
    }

    @Test void httpOrderRejectsClientAmountAndAnotherUsersBooking() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payments/create-order")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("alice").roles("PASSENGER"))
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"pnr\":\""+PNR+"\",\"amount\":1}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payments/create-order")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("bob").roles("PASSENGER"))
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"pnr\":\""+PNR+"\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        assertEquals(0,records.count()); assertEquals(0,captures.count());
        verify(provider,never()).createOrder(anyString(),anyLong(),anyString());
    }

    @Test void paidWaitlistIsAcceptedWithoutClaimingAConfirmedSeatAndLaterRefunds() {
        existingOrder();
        when(provider.fetchPayment("pay_wait")).thenReturn(captured("pay_wait"));
        when(reservations.confirm(eq(PNR),any())).thenReturn(new PaymentConfirmationResult(PNR,"WAITING",false,BigDecimal.ZERO));
        when(reservations.context(PNR)).thenReturn(new ReservationPaymentContext(PNR,"alice",FARE,"INR","WAITING",NOW.plusSeconds(120),"pay_wait",BigDecimal.ZERO,"NONE"));
        var result=service.verifyPayment("alice",verification("pay_wait"));
        assertEquals("SUCCESS",result.paymentStatus());assertEquals("WAITING",result.bookingStatus());assertFalse(result.payments().getFirst().bookingConfirmed());
        refunds(FARE);when(provider.fetchOrderPayments("order_1")).thenReturn(List.of(captured("pay_wait")));
        service.reconcileDue();
        assertEquals("PROCESSED",captures.findById("pay_wait").orElseThrow().getRefundStatus());
        verify(provider,times(1)).refund(eq("pay_wait"),eq(12550L),anyString());
    }
    @Test void createsOneReusableOrderWithTheSavedFare() {
        when(provider.createOrder(anyString(),eq(12550L),eq("INR"))).thenAnswer(i ->
                new PaymentProvider.Order("order_1",12550,"INR",i.getArgument(0),"created"));
        var first = service.createOrder("alice", new PaymentOrderReq(PNR,null));
        var repeated = service.createOrder("alice", new PaymentOrderReq(PNR,null));
        assertEquals("order_1", repeated.getRazorpayOrderId());
        assertEquals(12550, first.getAmountInPaise());
        assertEquals(FARE, first.getAmount());
        assertEquals("TEST", first.getMode());
        assertEquals(NOW.plusSeconds(300), first.getHoldExpiresAt());
        assertEquals(1, records.count());
        verify(provider,times(1)).createOrder(anyString(),eq(12550L),eq("INR"));
    }

    @Test void rejectsClientChosenFareBeforeAnyProviderCall() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createOrder("alice",new PaymentOrderReq(PNR,BigDecimal.ONE)));
        verifyNoInteractions(provider);
        assertEquals(0,records.count());
    }

    @Test void refusesCheckoutWhenHoldExpiresDuringProviderCreation() {
        when(provider.createOrder(anyString(),eq(12550L),eq("INR"))).thenAnswer(i -> {
            when(reservations.context(PNR)).thenReturn(context("EXPIRED", NOW, FARE));
            return new PaymentProvider.Order("order_1",12550,"INR",i.getArgument(0),"created");
        });
        assertStatus(HttpStatus.CONFLICT, () -> service.createOrder("alice",new PaymentOrderReq(PNR,null)));
        // Preserve the provider order for late-payment recovery; never extend the original hold.
        assertEquals("order_1",records.findByPnr(PNR).orElseThrow().getRazorpayOrderId());
        assertEquals(NOW.plusSeconds(300),records.findByPnr(PNR).orElseThrow().getHoldExpiresAt());
    }

    @Test void rejectsProviderOrderThatDoesNotMatchSavedFareOrReceipt() {
        when(provider.createOrder(anyString(),anyLong(),anyString())).thenReturn(
                new PaymentProvider.Order("order_1",1,"INR","wrong-receipt","created"));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.createOrder("alice",new PaymentOrderReq(PNR,null)));
        assertNull(records.findByPnr(PNR).orElseThrow().getRazorpayOrderId());
        assertEquals(0,captures.count());
    }

    @Test void rejectsAnotherUsersBookingAndExpiredOrLegacyBookings() {
        assertStatus(HttpStatus.FORBIDDEN, () -> service.createOrder("mallory",new PaymentOrderReq(PNR,null)));
        when(reservations.context(PNR)).thenReturn(context("PENDING_PAYMENT",NOW,FARE));
        assertStatus(HttpStatus.CONFLICT, () -> service.createOrder("alice",new PaymentOrderReq(PNR,null)));
        when(reservations.context(PNR)).thenReturn(context("LEGACY",null,null));
        assertStatus(HttpStatus.CONFLICT, () -> service.createOrder("alice",new PaymentOrderReq(PNR,null)));
        verify(provider,never()).createOrder(anyString(),anyLong(),anyString());
        assertNull(service.status("alice",false,PNR).amount());
    }

    @Test void verifiesOwnershipBeforeSignatureOrProviderFetch() {
        existingOrder();
        assertStatus(HttpStatus.FORBIDDEN, () -> service.verifyPayment("mallory",verification("pay_1")));
        verify(provider,never()).verifyCheckout(anyString(),anyString(),anyString());
        verify(provider,never()).fetchPayment(anyString());
        assertStatus(HttpStatus.FORBIDDEN, () -> service.status("mallory",false,PNR));
        assertNotNull(service.status("admin",true,PNR));
    }

    @Test void rejectsInvalidSignatureWithoutMutatingPaymentState() {
        existingOrder();
        doThrow(new PaymentException(HttpStatus.BAD_REQUEST,"Invalid signature"))
                .when(provider).verifyCheckout(anyString(),anyString(),anyString());
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.verifyPayment("alice",verification("pay_1")));
        assertEquals(PaymentRecord.PaymentStatus.CREATED,records.findByPnr(PNR).orElseThrow().getStatus());
        assertEquals(0,captures.count());
        verify(provider,never()).fetchPayment(anyString());
    }

    @Test void rejectsWrongPaymentAmountCurrencyAndOrder() {
        existingOrder();
        for (var payment : List.of(
                new PaymentProvider.Payment("pay_1","order_1",1,"INR","captured",true,0),
                new PaymentProvider.Payment("pay_1","order_1",12550,"USD","captured",true,0),
                new PaymentProvider.Payment("pay_1","order_other",12550,"INR","captured",true,0))) {
            when(provider.fetchPayment("pay_1")).thenReturn(payment);
            assertStatus(HttpStatus.BAD_REQUEST, () -> service.verifyPayment("alice",verification("pay_1")));
        }
        assertEquals(0,captures.count());
        verify(reservations,never()).confirm(anyString(),any());
    }

    @Test void confirmsCapturedPaymentAndSafelyReplaysDuplicateVerification() {
        existingOrder(); confirmNormally();
        when(provider.fetchPayment("pay_1")).thenReturn(captured("pay_1"));
        service.verifyPayment("alice",verification("pay_1"));
        service.verifyPayment("alice",verification("pay_1"));
        assertEquals(1,captures.count());
        assertTrue(captures.findById("pay_1").orElseThrow().isConfirmed());
        assertEquals(PaymentRecord.PaymentStatus.SUCCESS,records.findByPnr(PNR).orElseThrow().getStatus());
        verify(reservations,atLeastOnce()).confirm(PNR,new PaymentConfirmation("order_1","pay_1",FARE,"INR"));
    }

    @Test void capturesAuthorizedTestPaymentBeforeConfirmation() {
        existingOrder(); confirmNormally();
        when(provider.fetchPayment("pay_1")).thenReturn(new PaymentProvider.Payment("pay_1","order_1",12550,"INR","authorized",false,0));
        when(provider.capturePayment("pay_1",12550,"INR")).thenReturn(captured("pay_1"));
        service.verifyPayment("alice",verification("pay_1"));
        verify(provider).capturePayment("pay_1",12550,"INR");
        assertTrue(captures.findById("pay_1").orElseThrow().isConfirmed());
    }

    @Test void failedAttemptLeavesOrderAvailableAndDoesNotConfirm() {
        existingOrder();
        when(provider.fetchPayment("pay_1")).thenReturn(new PaymentProvider.Payment("pay_1","order_1",12550,"INR","failed",false,0));
        service.verifyPayment("alice",verification("pay_1"));
        assertEquals(0,captures.count());
        assertEquals(PaymentRecord.PaymentStatus.CREATED,records.findByPnr(PNR).orElseThrow().getStatus());
        verify(reservations,never()).confirm(anyString(),any());
    }

    @Test void refundsExpiredBookingInFullWithoutDuplicatingRefundOnReplay() {
        existingOrder(); refunds(FARE);
        when(provider.fetchPayment("pay_1")).thenReturn(captured("pay_1"));
        service.verifyPayment("alice",verification("pay_1"));
        service.verifyPayment("alice",verification("pay_1"));
        var c = captures.findById("pay_1").orElseThrow();
        assertEquals(FARE,c.getRefundAmount());
        assertEquals("PROCESSED",c.getRefundStatus());
        assertFalse(c.isConfirmed());
        verify(provider,times(1)).refund(eq("pay_1"),eq(12550L),anyString());
        verify(reservations,atLeastOnce()).refundStatus(PNR,"pay_1","PROCESSED",FARE);
    }

    @Test void duplicateCaptureGetsFullRefundWithoutChangingOriginalConfirmation() {
        existingOrder(); confirmNormally();
        when(provider.fetchPayment("pay_1")).thenReturn(captured("pay_1"));
        service.verifyPayment("alice",verification("pay_1"));
        refunds(FARE);
        when(provider.fetchPayment("pay_2")).thenReturn(captured("pay_2"));
        service.verifyPayment("alice",verification("pay_2"));
        assertEquals(2,captures.count());
        assertTrue(captures.findById("pay_1").orElseThrow().isConfirmed());
        assertEquals("PROCESSED",captures.findById("pay_2").orElseThrow().getRefundStatus());
        assertEquals("pay_1",records.findByPnr(PNR).orElseThrow().getRazorpayPaymentId());
    }

    @Test void reconciliationRecoversAfterReservationConfirmationResponseIsLost() {
        existingOrder();
        when(provider.fetchPayment("pay_1")).thenReturn(captured("pay_1"));
        when(reservations.confirm(eq(PNR),any())).thenThrow(new PaymentException(HttpStatus.SERVICE_UNAVAILABLE,"timeout"))
                .thenReturn(new PaymentConfirmationResult(PNR,"CONFIRMED",false,BigDecimal.ZERO));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.verifyPayment("alice",verification("pay_1")));
        assertEquals(1,captures.count());
        assertFalse(captures.findById("pay_1").orElseThrow().isConfirmed());
        service.reconcileDue();
        assertTrue(captures.findById("pay_1").orElseThrow().isConfirmed());
    }

    @Test void reconciliationDiscoversCaptureEvenWhenBrowserClosesWithoutCallback() {
        existingOrder(); confirmNormally();
        when(provider.fetchOrderPayments("order_1")).thenReturn(List.of(captured("pay_1")));
        when(provider.fetchPayment("pay_1")).thenReturn(captured("pay_1"));
        service.reconcileDue();
        assertEquals(PaymentRecord.PaymentStatus.SUCCESS,records.findByPnr(PNR).orElseThrow().getStatus());
    }

    @Test void lostRefundResponseIsRecoveredUsingDurableReceiptWithoutAnotherPost() {
        existingOrder(); refunds(FARE);
        when(provider.fetchPayment("pay_1")).thenReturn(captured("pay_1"));
        when(provider.refund(anyString(),anyLong(),anyString())).thenThrow(new PaymentException(HttpStatus.SERVICE_UNAVAILABLE,"timeout"));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.verifyPayment("alice",verification("pay_1")));
        var c = captures.findById("pay_1").orElseThrow();
        assertEquals("PENDING",c.getRefundStatus());
        when(provider.fetchRefunds("pay_1")).thenReturn(List.of(new PaymentProvider.Refund("rfnd_1","pay_1",12550,"processed",c.getRefundKey())));
        service.reconcileDue();
        assertEquals("PROCESSED",captures.findById("pay_1").orElseThrow().getRefundStatus());
        verify(provider,times(1)).refund(anyString(),anyLong(),anyString());
    }

    @Test void lostOrderResponseIsRecoveredByReceiptAfterTheLeaseExpires() {
        when(provider.createOrder(anyString(),anyLong(),anyString())).thenThrow(new PaymentException(HttpStatus.SERVICE_UNAVAILABLE,"timeout"));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.createOrder("alice",new PaymentOrderReq(PNR,null)));
        var row = records.findByPnr(PNR).orElseThrow();
        when(clock.instant()).thenReturn(NOW.plusSeconds(61));
        when(provider.findOrderByReceipt(row.getReceipt())).thenReturn(Optional.of(new PaymentProvider.Order("order_1",12550,"INR",row.getReceipt(),"created")));
        assertEquals("order_1",service.createOrder("alice",new PaymentOrderReq(PNR,null)).getRazorpayOrderId());
        verify(provider,times(1)).createOrder(anyString(),anyLong(),anyString());
    }

    @Test void cancellationAfterConfirmationIsDiscoveredAndRefundedAtSavedAmount() {
        existingOrder(); confirmNormally();
        when(provider.fetchPayment("pay_1")).thenReturn(captured("pay_1"));
        service.verifyPayment("alice",verification("pay_1"));
        BigDecimal refund = new BigDecimal("100.40");
        refunds(refund);
        service.reconcileDue();
        assertEquals(refund,captures.findById("pay_1").orElseThrow().getRefundAmount());
        verify(provider).refund(eq("pay_1"),eq(10040L),anyString());
    }

    @Test void webhookSignaturePrecedesParsingAndDurableInboxDeduplicatesSignedBodies() {
        existingOrder(); confirmNormally();
        when(provider.fetchPayment("pay_1")).thenReturn(captured("pay_1"));
        byte[] body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_1\"}}}}".getBytes(StandardCharsets.UTF_8);
        doThrow(new PaymentException(HttpStatus.BAD_REQUEST,"signature")).when(provider).verifyWebhook(body,"bad");
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.webhook(body,"bad"));
        assertEquals(0,webhooks.count());
        service.webhook(body,"valid"); service.webhook(body,"valid");
        assertEquals(1,webhooks.count());
        service.reconcileDue();
        assertTrue(webhooks.findAll().getFirst().isProcessed());
        assertTrue(captures.findById("pay_1").orElseThrow().isConfirmed());
    }

    @Test void concurrentOrderRequestsCreateOneProviderOrder() throws Exception {
        AtomicInteger providerCreates = new AtomicInteger();
        when(provider.createOrder(anyString(),anyLong(),anyString())).thenAnswer(i -> {
            providerCreates.incrementAndGet();
            return new PaymentProvider.Order("order_1",12550,"INR",i.getArgument(0),"created");
        });
        try (var pool = Executors.newFixedThreadPool(6)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> results = new ArrayList<>();
            for (int i=0;i<6;i++) results.add(pool.submit(() -> {
                start.await();
                try { return service.createOrder("alice",new PaymentOrderReq(PNR,null)).getRazorpayOrderId(); }
                catch (PaymentException e) { assertEquals(HttpStatus.CONFLICT,e.getStatus()); return "retry"; }
            }));
            start.countDown();
            for (var result : results) assertTrue(Set.of("order_1","retry").contains(result.get(20,TimeUnit.SECONDS)));
        }
        assertEquals(1,records.count()); assertEquals(1,providerCreates.get());
    }

    @Test void concurrentVerificationCreatesOneCapture() throws Exception {
        existingOrder(); confirmNormally();
        when(provider.fetchPayment("pay_1")).thenReturn(captured("pay_1"));
        try (var pool = Executors.newFixedThreadPool(6)) {
            List<Future<PaymentStatusRes>> results = new ArrayList<>();
            for(int i=0;i<6;i++) results.add(pool.submit(() -> service.verifyPayment("alice",verification("pay_1"))));
            for(var result:results) assertEquals("SUCCESS",result.get(20,TimeUnit.SECONDS).paymentStatus());
        }
        assertEquals(1,captures.count());
    }

    @Test void stalePendingRefundCannotDowngradeProcessedRefund() {
        var row = existingOrder();
        store.captured(row,captured("pay_1"),NOW);
        var capture = store.prepareRefund("pay_1",FARE,NOW);
        store.refundUpdated("pay_1",new PaymentProvider.Refund("rfnd_1","pay_1",12550,"processed",capture.getRefundKey()),NOW);
        store.refundUpdated("pay_1",new PaymentProvider.Refund("rfnd_1","pay_1",12550,"pending",capture.getRefundKey()),NOW);
        assertEquals("PROCESSED",captures.findById("pay_1").orElseThrow().getRefundStatus());
    }

    @Test void concurrentRefundRequestsReuseOneDurableReceipt() throws Exception {
        existingOrder(); refunds(FARE);
        when(provider.fetchPayment("pay_1")).thenReturn(captured("pay_1"));
        Set<String> receipts = ConcurrentHashMap.newKeySet();
        when(provider.refund(eq("pay_1"),eq(12550L),anyString())).thenAnswer(i -> {
            receipts.add(i.getArgument(2));
            return new PaymentProvider.Refund("rfnd_1","pay_1",12550,"processed",i.getArgument(2));
        });
        try (var pool = Executors.newFixedThreadPool(6)) {
            List<Future<PaymentStatusRes>> results = new ArrayList<>();
            for(int i=0;i<6;i++) results.add(pool.submit(() -> service.verifyPayment("alice",verification("pay_1"))));
            for(var result:results) result.get(20,TimeUnit.SECONDS);
        }
        assertEquals(1,captures.count());
        assertEquals(1,receipts.size());
        assertEquals("PROCESSED",captures.findById("pay_1").orElseThrow().getRefundStatus());
    }

    @Test void verifiedFailedAttemptNotifiesReservationWithoutConfirmingOrBlockingRetry() {
        existingOrder();when(provider.fetchPayment("pay_failed")).thenReturn(new PaymentProvider.Payment("pay_failed","order_1",12550,"INR","failed",false,0));
        service.verifyPayment("alice",verification("pay_failed"));
        verify(reservations).paymentFailed(PNR);verify(reservations,never()).confirm(anyString(),any());assertEquals(0,captures.count());
        assertEquals(PaymentRecord.PaymentStatus.CREATED,records.findByPnr(PNR).orElseThrow().getStatus());
        when(provider.fetchPayment("pay_retry")).thenReturn(captured("pay_retry"));confirmNormally();service.verifyPayment("alice",verification("pay_retry"));assertEquals(1,captures.count());
    }
}
