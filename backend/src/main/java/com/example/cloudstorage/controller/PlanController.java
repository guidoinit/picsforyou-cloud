package com.example.cloudstorage.controller;

import com.example.cloudstorage.model.AppUser;
import com.example.cloudstorage.model.SubscriptionPlan;
import com.example.cloudstorage.model.UserSession;
import com.example.cloudstorage.repository.AppUserRepository;
import com.example.cloudstorage.repository.SubscriptionPlanRepository;
import com.example.cloudstorage.repository.UserSessionRepository;
import com.example.cloudstorage.service.MailService;
import com.example.cloudstorage.service.StripePaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AppUserRepository appUserRepository;
    private final UserSessionRepository userSessionRepository;
    private final StripePaymentService stripePaymentService;
    private final MailService mailService;

    private static final long FREE_PRICE_CENTS = 0;
    private static final long BASE_PRICE_CENTS = 599;
    private static final long PRO_PRICE_CENTS = 2000;

    public PlanController(SubscriptionPlanRepository subscriptionPlanRepository,
                          AppUserRepository appUserRepository,
                          UserSessionRepository userSessionRepository,
                          StripePaymentService stripePaymentService,
                          MailService mailService) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.appUserRepository = appUserRepository;
        this.userSessionRepository = userSessionRepository;
        this.stripePaymentService = stripePaymentService;
        this.mailService = mailService;
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionPlan>> listPlans() {
        return ResponseEntity.ok(subscriptionPlanRepository.findAll());
    }

    @PostMapping("/select")
    public ResponseEntity<?> selectPlan(@RequestBody Map<String, String> body,
                                        @CookieValue(value = "session", required = false) String sessionId) {
        String planId = body.get("planId");
        if (planId == null || planId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request", "message", "planId is required"));
        }

        if (sessionId == null) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "Unauthorized", "message", "Not logged in"));
        }

        var sessionOpt = userSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                "error", "Unauthorized", "message", "Invalid session"));
        }

        var planOpt = subscriptionPlanRepository.findById(planId);
        if (planOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request", "message", "Invalid planId"));
        }

        UserSession session = sessionOpt.get();
        Optional<AppUser> userOpt = appUserRepository.findById(session.getUserId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "Not Found", "message", "User not found"));
        }

        AppUser user = userOpt.get();
        SubscriptionPlan targetPlan = planOpt.get();

        if (user.isCustomPlanPending() && !planId.equals("custom")) {
            return ResponseEntity.ok(Map.of(
                "status", "PENDING_RESPONSE",
                "message", "Pending response – You have a pending custom plan request. Wait for our team to contact you."
            ));
        }

        long currentPrice = getPlanPriceCents(user.getSubscriptionPlanId());
        long targetPrice = getPlanPriceCents(planId);

        // Free/Custom → no payment or refund needed
        if ((planId.equals("free") || planId.equals("custom") || currentPrice == targetPrice)
            && (user.getStripePaymentIntentId() == null || user.getStripePaymentIntentId().isBlank())) {
            // Never paid and going to free/custom/same price → just assign
            user.setSubscriptionPlanId(planId);
            appUserRepository.save(user);
            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Plan changed to " + targetPlan.getName(),
                "plan", planId,
                "planLimitMb", targetPlan.getStorageLimitMb(),
                "planPrice", targetPlan.getPrice()
            ));
        }

        // Same plan → no change
        if (user.getSubscriptionPlanId().equals(planId)) {
            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Already on " + targetPlan.getName(),
                "plan", planId,
                "planLimitMb", targetPlan.getStorageLimitMb(),
                "planPrice", targetPlan.getPrice()
            ));
        }

        // Downgrade (going from paid to cheaper or free)
        if (targetPrice < currentPrice) {
            // If paid → free or custom, no refund
            if (planId.equals("free") || planId.equals("custom")) {
                user.setSubscriptionPlanId(planId);
                user.setStripeAmountPaid(0L);
                user.setStripePaymentIntentId(null);
                appUserRepository.save(user);
                return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Plan downgraded to " + targetPlan.getName(),
                    "plan", planId,
                    "planLimitMb", targetPlan.getStorageLimitMb(),
                    "planPrice", targetPlan.getPrice()
                ));
            }

            // Paid → cheaper paid: issue refund for difference
            long diffCents = currentPrice - targetPrice;
            String pi = user.getStripePaymentIntentId();
            if (pi == null || pi.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request", "message", "No payment record found for refund"));
            }
            try {
                stripePaymentService.refundPayment(pi, diffCents);
            } catch (Exception e) {
                return ResponseEntity.status(500).body(Map.of(
                    "error", "Refund Error", "message", e.getMessage()));
            }

            user.setSubscriptionPlanId(planId);
            user.setStripeAmountPaid(targetPrice);
            appUserRepository.save(user);
            return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Plan downgraded to " + targetPlan.getName() + ". Refund of €" + String.format(Locale.US, "%.2f", diffCents / 100.0) + " initiated.",
                "plan", planId,
                "planLimitMb", targetPlan.getStorageLimitMb(),
                "planPrice", targetPlan.getPrice()
            ));
        }

        // Upgrade (going from cheaper to more expensive)
        if (targetPrice > currentPrice) {
            long diffCents = targetPrice - currentPrice;
            return ResponseEntity.ok(Map.of(
                "status", "UPGRADE_REQUIRED",
                "message", "Pay the difference of €" + String.format(Locale.US, "%.2f", diffCents / 100.0) + " to upgrade to " + targetPlan.getName(),
                "plan", planId,
                "diffCents", diffCents,
                "email", user.getEmail()
            ));
        }

        user.setSubscriptionPlanId(planId);
        appUserRepository.save(user);
        SubscriptionPlan plan = planOpt.get();
        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "message", "Plan upgraded to " + plan.getName(),
            "plan", planId,
            "planLimitMb", plan.getStorageLimitMb(),
            "planPrice", plan.getPrice()
        ));
    }

    @PostMapping("/assign")
    public ResponseEntity<?> assignPlan(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String planId = body.get("planId");
        if (email == null || email.isBlank() || planId == null || planId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request", "message", "email and planId are required"));
        }

        var planOpt = subscriptionPlanRepository.findById(planId);
        if (planOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request", "message", "Invalid planId"));
        }

        Optional<AppUser> userOpt = appUserRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "Not Found", "message", "User not found"));
        }

        AppUser user = userOpt.get();
        user.setSubscriptionPlanId(planId);
        appUserRepository.save(user);

        SubscriptionPlan plan = planOpt.get();
        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "message", "Plan assigned to " + plan.getName(),
            "plan", planId,
            "planLimitMb", plan.getStorageLimitMb(),
            "planPrice", plan.getPrice()
        ));
    }

    @PostMapping("/create-checkout")
    public ResponseEntity<?> createCheckout(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String planId = body.get("planId");
        String diffCentsStr = body.get("diffCents");

        if (email == null || email.isBlank() || planId == null || planId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request", "message", "email and planId are required"));
        }

        try {
            Map<String, Object> result;
            if (diffCentsStr != null && !diffCentsStr.isBlank()) {
                long diffCents = Long.parseLong(diffCentsStr);
                result = stripePaymentService.createCheckoutSession(email, planId, diffCents);
            } else {
                result = stripePaymentService.createCheckoutSession(email, planId);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Payment Error", "message", e.getMessage()));
        }
    }

    @GetMapping("/checkout-success")
    public ResponseEntity<?> checkoutSuccess(
            @RequestParam("session_id") String sessionId,
            @RequestParam("email") String email,
            @RequestParam("planId") String planId) {

        var planOpt = subscriptionPlanRepository.findById(planId);
        if (planOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request", "message", "Invalid planId"));
        }

        SubscriptionPlan plan = planOpt.get();

        try {
            Map<String, Object> verification = stripePaymentService.verifyCheckoutSession(sessionId);
            boolean paid = (boolean) verification.get("paid");
            if (!paid) {
                String html = """
                    <!DOCTYPE html><html><head><meta charset="UTF-8"><title>Payment Failed</title>
                    <style>body{background:#0a0a0a;color:#eee;font-family:monospace;display:flex;justify-content:center;align-items:center;height:100vh;}
                    .box{background:#111;border:1px solid #2a2a2a;border-radius:12px;padding:40px;text-align:center;max-width:480px;}
                    h1{color:#FF3E00;}p{color:#999;}</style></head>
                    <body><div class="box"><h1>\u2715 Payment Failed</h1><p>Your payment was not completed. Please try again.</p>
                    <a href="http://localhost:5173" style="color:#FF3E00;">\u2190 Back to picsforyou.cloud</a></div></body></html>
                    """;
                return ResponseEntity.ok().header("Content-Type", "text/html").body(html);
            }

            String paymentIntentId = (String) verification.get("paymentIntentId");

            Optional<AppUser> userOpt = appUserRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                    "error", "Not Found", "message", "User not found"));
            }

            AppUser user = userOpt.get();
            user.setSubscriptionPlanId(planId);
            user.setStripePaymentIntentId(paymentIntentId);
            user.setStripeAmountPaid(getPlanPriceCents(planId));
            appUserRepository.save(user);

            String planName = plan.getName();
            String html = """
                <!DOCTYPE html><html><head><meta charset="UTF-8"><title>Payment Successful!</title>
                <style>body{background:#0a0a0a;color:#eee;font-family:monospace;display:flex;justify-content:center;align-items:center;height:100vh;}
                .box{background:#111;border:1px solid #2a2a2a;border-radius:12px;padding:40px;text-align:center;max-width:480px;}
                h1{color:#FF3E00;}p{color:#999;line-height:1.6;}.btn{display:inline-block;margin-top:20px;padding:12px 24px;
                background:#FF3E00;color:#fff;text-decoration:none;border-radius:8px;font-weight:bold;font-size:13px;}</style></head>
                <body><div class="box">
                <div style="font-size:48px;margin-bottom:16px;">\u2713</div>
                <h1>Payment Successful!</h1>
                <p>Your <strong style="color:#eee;">%s</strong> plan is now active.<br>Login to start using the Cloud Storage API.</p>
                <a class="btn" href="http://localhost:5173">LOGIN TO PICKSFORYOU.CLOUD</a>
                </div></body></html>
                """.formatted(planName);
            return ResponseEntity.ok().header("Content-Type", "text/html").body(html);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Payment Error", "message", e.getMessage()));
        }
    }

    @PostMapping("/custom-request")
    public ResponseEntity<?> customRequest(@RequestBody Map<String, String> body,
                                            @CookieValue(value = "session", required = false) String sessionId) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request", "message", "text is required"));
        }
        if (text.length() > 512) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request", "message", "Text must be 512 characters or less"));
        }

        String email = body.get("email");
        Optional<AppUser> userOpt;

        if (email != null && !email.isBlank()) {
            userOpt = appUserRepository.findByEmail(email);
        } else if (sessionId != null) {
            var sessionOpt = userSessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of(
                    "error", "Unauthorized", "message", "Invalid session"));
            }
            userOpt = appUserRepository.findById(sessionOpt.get().getUserId());
        } else {
            return ResponseEntity.status(401).body(Map.of(
                "error", "Unauthorized", "message", "Not logged in"));
        }

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                "error", "Not Found", "message", "User not found"));
        }

        AppUser user = userOpt.get();
        String currentPlan = user.getSubscriptionPlanId();
        String currentPlanName = switch (currentPlan) {
            case "free" -> "Free";
            case "base" -> "Base (€5.99)";
            case "professional" -> "Professional (€20.00)";
            default -> currentPlan;
        };

        mailService.sendCustomPlanRequestEmail(user.getEmail(), currentPlanName, text);

        user.setCustomPlanPending(true);
        appUserRepository.save(user);

        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "message", "Custom plan request sent. We will contact you soon.",
            "customPlanPending", true
        ));
    }

    private long getPlanPriceCents(String planId) {
        if (planId == null) return FREE_PRICE_CENTS;
        return switch (planId) {
            case "free", "custom" -> FREE_PRICE_CENTS;
            case "base" -> BASE_PRICE_CENTS;
            case "professional" -> PRO_PRICE_CENTS;
            default -> FREE_PRICE_CENTS;
        };
    }
}
