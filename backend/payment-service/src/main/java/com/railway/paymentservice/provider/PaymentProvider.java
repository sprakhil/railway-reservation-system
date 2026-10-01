package com.railway.paymentservice.provider;
import java.util.List;
import java.util.Optional;
public interface PaymentProvider {
    void requireTestConfiguration();
    String publicKey();
    void verifyCheckout(String orderId, String paymentId, String signature);
    void verifyWebhook(byte[] body, String signature);
    Optional<Order> findOrderByReceipt(String receipt);
    Order createOrder(String receipt, long amount, String currency);
    Order fetchOrder(String orderId);
    Payment fetchPayment(String paymentId);
    Payment capturePayment(String paymentId, long amount, String currency);
    List<Payment> fetchOrderPayments(String orderId);
    Refund refund(String paymentId, long amount, String idempotencyKey);
    Refund fetchRefund(String refundId);
    List<Refund> fetchRefunds(String paymentId);
    record Order(String id, long amount, String currency, String receipt, String status) {}
    record Payment(String id, String orderId, long amount, String currency, String status, boolean captured, long amountRefunded) {}
    record Refund(String id, String paymentId, long amount, String status, String receipt) {}
}
