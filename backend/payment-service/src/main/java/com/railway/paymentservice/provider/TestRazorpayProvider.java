package com.railway.paymentservice.provider;

import com.railway.paymentservice.exception.PaymentException;
import org.json.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

@Component
public class TestRazorpayProvider implements PaymentProvider {
    private final String keyId;
    private final String secret;
    private final String webhookSecret;
    private final RestClient client;

    @Autowired
    public TestRazorpayProvider(@Value("${razorpay.test.key-id:}") String keyId,
            @Value("${razorpay.test.key-secret:}") String secret,
            @Value("${razorpay.test.webhook-secret:}") String webhookSecret) {
        this(keyId, secret, webhookSecret, httpBuilder());
    }

    TestRazorpayProvider(String keyId, String secret, String webhookSecret, RestClient.Builder builder) {
        this.keyId = keyId; this.secret = secret; this.webhookSecret = webhookSecret;
        if (!keyId.isBlank() && !keyId.startsWith("rzp_test_")) {
            throw new IllegalStateException("Only Razorpay test keys are permitted. Live payments are disabled.");
        }
        client = builder.baseUrl("https://api.razorpay.com/v1")
                .defaultHeaders(headers -> headers.setBasicAuth(keyId, secret)).build();
    }

    private static RestClient.Builder httpBuilder() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory);
    }

    @Override public void requireTestConfiguration() {
        if (!keyId.startsWith("rzp_test_") || keyId.length() <= 9 || secret.isBlank())
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "Configure Razorpay TEST credentials to use simulated checkout. Live payments are disabled.");
    }
    @Override public String publicKey() { requireTestConfiguration(); return keyId; }
    @Override public void verifyCheckout(String orderId, String paymentId, String signature) {
        requireTestConfiguration();
        verify((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8), signature, secret);
    }
    @Override public void verifyWebhook(byte[] body, String signature) {
        requireTestConfiguration();
        if (webhookSecret.isBlank())
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "Test webhook secret is not configured");
        verify(body, signature, webhookSecret);
    }
    private static void verify(byte[] payload, String signature, String key) {
        try {
            if (signature == null || !signature.matches("[0-9a-fA-F]{64}"))
                throw new IllegalArgumentException();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal(payload), HexFormat.of().parseHex(signature)))
                throw new IllegalArgumentException();
        } catch (Exception ex) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "Invalid payment signature");
        }
    }
    private JSONObject get(String path, Object... variables) {
        requireTestConfiguration();
        try {
            String json = client.get().uri(path, variables).retrieve().body(String.class);
            return new JSONObject(json);
        } catch (Exception ex) { throw unavailable(); }
    }
    private JSONObject post(String path, JSONObject body, Object... variables) {
        requireTestConfiguration();
        try {
            var request = client.post().uri(path, variables).contentType(MediaType.APPLICATION_JSON);
            return new JSONObject(request.body(body.toString()).retrieve().body(String.class));
        } catch (Exception ex) { throw unavailable(); }
    }
    private PaymentException unavailable() {
        return new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay test service is unavailable; reconciliation will retry safely");
    }
    @Override public Optional<Order> findOrderByReceipt(String receipt) {
        JSONArray items = get("/orders?receipt={receipt}&count=100", receipt).getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            Order order = order(items.getJSONObject(i));
            if (receipt.equals(order.receipt())) return Optional.of(order);
        }
        return Optional.empty();
    }
    @Override public Order createOrder(String receipt, long amount, String currency) {
        return order(post("/orders", new JSONObject().put("receipt", receipt).put("amount", amount)
                .put("currency", currency).put("partial_payment", false)));
    }
    @Override public Order fetchOrder(String id) { return order(get("/orders/{id}", id)); }
    @Override public Payment fetchPayment(String id) { return payment(get("/payments/{id}", id)); }
    @Override public Payment capturePayment(String id, long amount, String currency) {
        return payment(post("/payments/{id}/capture", new JSONObject().put("amount", amount)
                .put("currency", currency), id));
    }
    @Override public List<Payment> fetchOrderPayments(String id) {
        JSONArray items = get("/orders/{id}/payments", id).getJSONArray("items");
        List<Payment> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) result.add(payment(items.getJSONObject(i)));
        return result;
    }
    @Override public Refund refund(String id, long amount, String key) {
        // Razorpay treats receipt as the refund idempotency key. It and the amount
        // are immutable in our database before this request. Retry by receipt lookup.
        return refund(post("/payments/{id}/refund", new JSONObject().put("amount", amount)
                .put("speed", "normal").put("receipt", key), id));
    }
    @Override public Refund fetchRefund(String id) { return refund(get("/refunds/{id}", id)); }
    @Override public List<Refund> fetchRefunds(String id) {
        List<Refund> result = new ArrayList<>();
        for (int skip = 0; ; skip += 100) {
            JSONArray items = get("/payments/{id}/refunds?count=100&skip={skip}", id, skip).getJSONArray("items");
            for (int i = 0; i < items.length(); i++) result.add(refund(items.getJSONObject(i)));
            if (items.length() < 100) return result;
        }
    }
    private static Order order(JSONObject o) {
        return new Order(o.getString("id"), o.getLong("amount"), o.getString("currency"),
                o.optString("receipt", ""), o.getString("status"));
    }
    private static Payment payment(JSONObject p) {
        return new Payment(p.getString("id"), p.optString("order_id", ""), p.getLong("amount"),
                p.getString("currency"), p.getString("status"), p.optBoolean("captured", false),
                p.optLong("amount_refunded", 0));
    }
    private static Refund refund(JSONObject r) {
        return new Refund(r.getString("id"), r.getString("payment_id"), r.getLong("amount"),
                r.getString("status"), r.optString("receipt", ""));
    }
}
