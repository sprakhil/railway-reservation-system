package com.railway.paymentservice.provider;

import com.railway.paymentservice.exception.PaymentException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TestRazorpayProviderTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TestRazorpayProvider provider = new TestRazorpayProvider("rzp_test_offline", "test-secret", "webhook-secret", builder);
    private static final String BASE = "https://api.razorpay.com/v1";

    @Test void liveCredentialsAreRejectedBeforeAnyNetworkOperation() {
        assertThrows(IllegalStateException.class, () -> new TestRazorpayProvider(
                "rzp_live_forbidden", "secret", "webhook", RestClient.builder()));
        server.verify();
    }

    @Test void missingCredentialsDisableEveryProviderOperation() {
        var disabled = new TestRazorpayProvider("", "", "", RestClient.builder());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(PaymentException.class, disabled::requireTestConfiguration).getStatus());
        assertThrows(PaymentException.class, () -> disabled.createOrder("receipt", 100, "INR"));
        assertThrows(PaymentException.class, () -> disabled.refund("pay_1", 100, "receipt"));
    }

    @Test void checkoutSignatureUsesSavedOrderIdAndRejectsTampering() throws Exception {
        String signature = sign("order_1|pay_1".getBytes(StandardCharsets.UTF_8), "test-secret");
        assertDoesNotThrow(() -> provider.verifyCheckout("order_1", "pay_1", signature));
        assertThrows(PaymentException.class, () -> provider.verifyCheckout("other_order", "pay_1", signature));
        assertThrows(PaymentException.class, () -> provider.verifyCheckout("order_1", "pay_2", signature));
        assertThrows(PaymentException.class, () -> provider.verifyCheckout("order_1", "pay_1", "bad"));
        assertThrows(PaymentException.class, () -> provider.verifyCheckout("order_1", "pay_1", null));
    }

    @Test void webhookSignatureUsesRawBytesAndSeparateSecret() throws Exception {
        byte[] body = "{ \"event\": \"payment.captured\" }".getBytes(StandardCharsets.UTF_8);
        String signature = sign(body, "webhook-secret");
        assertDoesNotThrow(() -> provider.verifyWebhook(body, signature));
        byte[] normalized = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        assertThrows(PaymentException.class, () -> provider.verifyWebhook(normalized, signature));
        assertThrows(PaymentException.class, () -> provider.verifyWebhook(body, sign(body, "test-secret")));
    }

    @Test void createsTestOrderWithExactIntegerPaiseAndNoPartialPayments() {
        server.expect(requestTo(BASE + "/orders")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic cnpwX3Rlc3Rfb2ZmbGluZTp0ZXN0LXNlY3JldA=="))
                .andExpect(content().json("{\"receipt\":\"rail_1\",\"amount\":12550,\"currency\":\"INR\",\"partial_payment\":false}"))
                .andRespond(withSuccess(orderJson(), MediaType.APPLICATION_JSON));
        var order = provider.createOrder("rail_1", 12550, "INR");
        assertEquals(12550, order.amount());
        assertEquals("rail_1", order.receipt());
        server.verify();
    }

    @Test void refundUsesPersistedReceiptForIdempotency() {
        server.expect(requestTo(BASE + "/payments/pay_1/refund")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"amount\":10040,\"speed\":\"normal\",\"receipt\":\"stable-refund-key\"}"))
                .andRespond(withSuccess("{\"id\":\"rfnd_1\",\"payment_id\":\"pay_1\",\"amount\":10040,\"status\":\"processed\",\"receipt\":\"stable-refund-key\"}", MediaType.APPLICATION_JSON));
        var refund = provider.refund("pay_1", 10040, "stable-refund-key");
        assertEquals("stable-refund-key", refund.receipt());
        assertEquals("processed", refund.status());
        server.verify();
    }

    @Test void orderRecoverySearchesReceiptAndChecksExactMatch() {
        server.expect(requestTo(BASE + "/orders?receipt=rail_1&count=100"))
                .andRespond(withSuccess("{\"items\":[" + orderJson() + "]}", MediaType.APPLICATION_JSON));
        assertEquals("order_1", provider.findOrderByReceipt("rail_1").orElseThrow().id());
        server.verify();
    }

    @Test void providerErrorsNeverExposeRawResponseOrCredentials() {
        server.expect(requestTo(BASE + "/payments/pay_1"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("private-provider-details"));
        var error = assertThrows(PaymentException.class, () -> provider.fetchPayment("pay_1"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertFalse(error.getMessage().contains("private-provider-details"));
        assertFalse(error.getMessage().contains("test-secret"));
        server.verify();
    }

    private static String orderJson() {
        return new JSONObject().put("id", "order_1").put("amount", 12550).put("currency", "INR")
                .put("receipt", "rail_1").put("status", "created").toString();
    }
    private static String sign(byte[] bytes, String secret) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(bytes));
    }
}
