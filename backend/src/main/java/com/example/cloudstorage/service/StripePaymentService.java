package com.example.cloudstorage.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.ProductData;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.Recurring;
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

    public Map<String, Object> createSubscriptionSession(String email, String planId) throws StripeException {
        long amountCents = getPlanAmount(planId);
        return createSubscriptionSession(email, planId, amountCents);
    }

    public Map<String, Object> createSubscriptionSession(String email, String planId, long amountCents) throws StripeException {
        String planName = switch (planId) {
            case "base" -> "Base";
            case "professional" -> "Professional";
            default -> throw new IllegalArgumentException("Invalid paid plan: " + planId);
        };

        String label = "picsforyou.cloud \u2013 " + planName + " Plan";

        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
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
                            .setRecurring(
                                Recurring.builder()
                                    .setInterval(Recurring.Interval.MONTH)
                                    .build()
                            )
                            .setProductData(
                                ProductData.builder()
                                    .setName(label)
                                    .setDescription(planName + " monthly subscription")
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
        log.info("Stripe Subscription Session created: {} ({} {}/month)", session.getId(), amountCents, "eur");

        return Map.of(
            "sessionId", session.getId(),
            "url", session.getUrl(),
            "amount", amountCents,
            "currency", "eur"
        );
    }

    public Map<String, Object> verifySubscriptionSession(String sessionId) throws StripeException {
        Session session = Session.retrieve(sessionId);
        String paymentStatus = session.getPaymentStatus();
        String subscriptionId = session.getSubscription();

        // Retrieve full subscription to check status
        String subscriptionStatus = "";
        if (subscriptionId != null) {
            Subscription sub = Subscription.retrieve(subscriptionId);
            subscriptionStatus = sub.getStatus();
        }

        log.info("Subscription Session {} paymentStatus: {}, subscription: {} (status: {})",
            sessionId, paymentStatus, subscriptionId, subscriptionStatus);

        boolean active = "paid".equals(paymentStatus)
            && ("active".equals(subscriptionStatus) || "trialing".equals(subscriptionStatus));

        return Map.of(
            "active", active,
            "subscriptionId", subscriptionId != null ? subscriptionId : ""
        );
    }

    public boolean cancelSubscription(String subscriptionId) throws StripeException {
        Subscription sub = Subscription.retrieve(subscriptionId);
        Subscription canceled = sub.cancel();
        log.info("Stripe subscription cancelled: {} (status: {})", subscriptionId, canceled.getStatus());
        return "canceled".equals(canceled.getStatus());
    }

    private long getPlanAmount(String planId) {
        return switch (planId) {
            case "base" -> 599L;
            case "professional" -> 2000L;
            default -> throw new IllegalArgumentException("Invalid paid plan: " + planId);
        };
    }
}
