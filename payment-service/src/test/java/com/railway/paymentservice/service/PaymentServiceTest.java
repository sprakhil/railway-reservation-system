package com.railway.paymentservice.service;

import com.railway.paymentservice.dto.PaymentOrderReq;
import com.railway.paymentservice.dto.PaymentOrderRes;
import com.railway.paymentservice.dto.PaymentVerificationReq;
import com.railway.paymentservice.entity.PaymentRecord;
import com.railway.paymentservice.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        // Inject the fake @Value keys before every test runs
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", "test_key_id");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "test_key_secret");
    }

    @Test
    void createOrder_Success_ReturnsOrderRes() {
        // Arrange
        String username = "johndoe";
        PaymentOrderReq req = new PaymentOrderReq();
        req.setPnr("PNR1234567");
        req.setAmount(new BigDecimal("1000"));

        // 1. INTERCEPT RazorpayClient instantiation (Extremely advanced Mockito)
        try (MockedConstruction<RazorpayClient> mockedRazorpay = mockConstruction(RazorpayClient.class,
                (mock, context) -> {
                    // Razorpay SDK uses public fields (mock.orders). We must mock the internal OrderClient too.
                    OrderClient mockOrderClient = mock(OrderClient.class);
                    mock.orders = mockOrderClient;

                    // Mock the Order object it returns
                    Order mockOrder = mock(Order.class);
                    when(mockOrder.get("id")).thenReturn("order_test_123");

                    // Tell the mock OrderClient to return our mock Order
                    when(mockOrderClient.create(any(JSONObject.class))).thenReturn(mockOrder);
                })) {

            when(paymentRepository.save(any(PaymentRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PaymentOrderRes response = paymentService.createOrder(username, req);

            // Assert
            assertNotNull(response);
            assertEquals("order_test_123", response.getRazorpayOrderId());
            assertEquals("CREATED", response.getStatus());
            verify(paymentRepository, times(1)).save(any(PaymentRecord.class));
        }
    }

    @Test
    void verifyPayment_SignatureValid_UpdatesStatusToSuccess() {
        // Arrange
        PaymentVerificationReq req = new PaymentVerificationReq();
        req.setRazorpayOrderId("order_test_123");
        req.setRazorpayPaymentId("pay_test_456");
        req.setRazorpaySignature("valid_signature");

        PaymentRecord mockRecord = new PaymentRecord();
        mockRecord.setRazorpayOrderId("order_test_123");
        mockRecord.setStatus(PaymentRecord.PaymentStatus.CREATED);

        when(paymentRepository.findByRazorpayOrderId("order_test_123")).thenReturn(Optional.of(mockRecord));
        when(paymentRepository.save(any(PaymentRecord.class))).thenReturn(mockRecord);

        // INTERCEPT the static Razorpay Utils class
        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            mockedUtils.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), eq("test_key_secret")))
                    .thenReturn(true);

            // Act
            String result = paymentService.verifyPayment(req);

            // Assert
            assertEquals("Payment Successful", result);
            assertEquals(PaymentRecord.PaymentStatus.SUCCESS, mockRecord.getStatus());
            assertEquals("pay_test_456", mockRecord.getRazorpayPaymentId());
        }
    }

    @Test
    void verifyPayment_SignatureInvalid_ThrowsExceptionAndSetsFailed() {
        // Arrange
        PaymentVerificationReq req = new PaymentVerificationReq();
        req.setRazorpayOrderId("order_test_123");

        PaymentRecord mockRecord = new PaymentRecord();
        mockRecord.setRazorpayOrderId("order_test_123");

        when(paymentRepository.findByRazorpayOrderId(anyString())).thenReturn(Optional.of(mockRecord));

        // INTERCEPT the static Razorpay Utils class
        try (MockedStatic<Utils> mockedUtils = mockStatic(Utils.class)) {
            // Force the signature validation to FAIL
            mockedUtils.when(() -> Utils.verifyPaymentSignature(any(JSONObject.class), anyString()))
                    .thenReturn(false);

            // Act & Assert
            Exception exception = assertThrows(RuntimeException.class, () -> paymentService.verifyPayment(req));

            assertTrue(exception.getMessage().contains("Invalid signature"));
            assertEquals(PaymentRecord.PaymentStatus.FAILED, mockRecord.getStatus());

            // Verify we still saved the FAILED status to the database
            verify(paymentRepository, times(1)).save(mockRecord);
        }
    }
}