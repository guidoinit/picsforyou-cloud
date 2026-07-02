package com.example.cloudstorage.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StripePaymentService {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentService.class);

    @Value("${stripe.secret-key}")
    private String secretKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    public Map<String, Object> createCheckoutSession(String email, String planId) throws StripeException {
        long amountCents = getPlanAmount(planId);
        return createCheckoutSession(email, planId, amountCents);
    }

    public Map<String, Object> createCheckoutSession(String email, String planId, long amountCents) throws StripeException {
        String planName = switch (planId) {
            case "base" -> "Base";
            case "professional" -> "Professional";
            default -> throw new IllegalArgumentException("Invalid paid plan: " + planId);
        };

        String label = (amountCents == getPlanAmount(planId))
            ? "picsforyou.cloud \u2013 " + planName + " Plan"
            : "picsforyou.cloud \u2013 " + planName + " Plan (upgrade)";

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
            .setSuccessUrl("http://localhost:3000/api/v1/plans/checkout-success?session_id={CHECKOUT_SESSION_ID}&email=" + email + "&planId=" + planId)
            .setCancelUrl("http://localhost:5173")
            .setCustomerEmail(email)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("eur")
                            .setUnitAmount(amountCents)
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(label)
                                    .setDescription(planName + " subscription")
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .putMetadata("plan_id", planId)
            .putMetadata("email", email)
            .build();

        Session session = Session.create(params);
        log.info("Stripe Checkout Session created: {} ({} {})", session.getId(), amountCents, "eur");

        return Map.of(
            "sessionId", session.getId(),
            "url", session.getUrl(),
            "amount", amountCents,
            "currency", "eur"
        );
    }

    public Map<String, Object> verifyCheckoutSession(String sessionId) throws StripeException {
        Session session = Session.retrieve(sessionId);
        String status = session.getPaymentStatus();
        String paymentIntentId = session.getPaymentIntent();
        log.info("Checkout Session {} payment status: {}, paymentIntent: {}", sessionId, status, paymentIntentId);
        return Map.of(
            "paid", "paid".equals(status),
            "paymentIntentId", paymentIntentId != null ? paymentIntentId : ""
        );
    }

    public boolean refundPayment(String paymentIntentId, long amountCents) throws StripeException {
        RefundCreateParams params = RefundCreateParams.builder()
            .setPaymentIntent(paymentIntentId)
            .setAmount(amountCents)
            .build();
        Refund refund = Refund.create(params);
        log.info("Refund created: {} for paymentIntent {} ({} cents)", refund.getId(), paymentIntentId, amountCents);
        return "succeeded".equals(refund.getStatus());
    }

    private long getPlanAmount(String planId) {
        return switch (planId) {
            case "base" -> 599L;
            case "professional" -> 2000L;
            default -> throw new IllegalArgumentException("Invalid paid plan: " + planId);
        };
    }
}
