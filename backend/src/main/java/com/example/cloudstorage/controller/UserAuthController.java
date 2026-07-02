package com.example.cloudstorage.controller;

import com.example.cloudstorage.dto.LoginRequest;
import com.example.cloudstorage.dto.LoginResponse;
import com.example.cloudstorage.dto.SignupRequest;
import com.example.cloudstorage.dto.VerifyRequest;
import com.example.cloudstorage.dto.VerifyResponse;
import com.example.cloudstorage.model.ApiCredential;
import com.example.cloudstorage.model.AppUser;
import com.example.cloudstorage.model.SubscriptionPlan;
import com.example.cloudstorage.model.UserSession;
import com.example.cloudstorage.repository.SubscriptionPlanRepository;
import com.example.cloudstorage.service.MailService;
import com.example.cloudstorage.service.UserAuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class UserAuthController {

    private final UserAuthService userAuthService;
    private final MailService mailService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public UserAuthController(UserAuthService userAuthService, MailService mailService,
                              SubscriptionPlanRepository subscriptionPlanRepository) {
        this.userAuthService = userAuthService;
        this.mailService = mailService;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        if (request.email() == null || request.password() == null) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Bad Request", "message", "Email and password are required"));
        }
        if (request.password().length() < 4) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Bad Request", "message", "Password must be at least 4 characters"));
        }

        if (userAuthService.emailExists(request.email())) {
            return ResponseEntity.status(409).body(
                Map.of("error", "Conflict", "message", "Email already registered"));
        }

        AppUser user = userAuthService.registerUser(request.email(), request.password());

        mailService.sendVerificationEmail(request.email(), user.getUsername(), user.getVerificationToken());

        return ResponseEntity.status(201).body(Map.of(
            "status", "SUCCESS",
            "message", "Registration submitted. Check your email for the verification code.",
            "email", request.email()
        ));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody VerifyRequest request) {
        if (request.token() == null || request.token().isBlank()) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Bad Request", "message", "Verification token is required"));
        }

        Optional<AppUser> userOpt = userAuthService.verifyEmail(request.token());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(400).body(
                Map.of("error", "Bad Request", "message", "Invalid or expired verification token"));
        }

        AppUser user = userOpt.get();
        ApiCredential cred = userAuthService.createApiCredential(user.getId(), user.getUsername());

        return ResponseEntity.ok(new VerifyResponse(
            "SUCCESS",
            "Email verified. API credentials generated.",
            cred.getClientId(),
            cred.getClientSecret(),
            cred.getScope()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if ((request.username() == null && request.email() == null) || request.password() == null) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Bad Request", "message", "Username/email and password are required"));
        }

        Optional<AppUser> userOpt = request.username() != null
            ? userAuthService.findByUsername(request.username())
            : userAuthService.findByEmail(request.email());

        if (userOpt.isEmpty() || !userAuthService.verifyPassword(request.password(), userOpt.get().getPasswordHash())) {
            return ResponseEntity.status(401).body(
                Map.of("error", "Unauthorized", "message", "Invalid username/email or password"));
        }

        AppUser user = userOpt.get();

        if (!user.isEmailVerified()) {
            return ResponseEntity.status(403).body(
                Map.of("error", "Forbidden", "message", "Email not verified. Please check your email for the verification code."));
        }

        UserSession session = userAuthService.createSession(user.getId(), user.getUsername());

        ResponseCookie cookie = ResponseCookie.from("session", session.getId())
            .httpOnly(true)
            .sameSite("Lax")
            .maxAge(Duration.ofDays(1))
            .path("/")
            .build();

        Optional<ApiCredential> credOpt = userAuthService.getApiCredentials(user.getId());
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Login successful");
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("storageUsedKb", user.getStorageUsedKb());
        response.put("customPlanPending", user.isCustomPlanPending());
        response.put("plan", user.getSubscriptionPlanId());
        subscriptionPlanRepository.findById(user.getSubscriptionPlanId()).ifPresent(plan -> {
            response.put("planLimitMb", plan.getStorageLimitMb());
            response.put("planPrice", plan.getPrice());
        });
        if (credOpt.isPresent()) {
            response.put("clientId", credOpt.get().getClientId());
            response.put("clientSecret", credOpt.get().getClientSecret());
            response.put("scope", credOpt.get().getScope());
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@CookieValue(value = "session", required = false) String sessionId) {
        if (sessionId == null) {
            return ResponseEntity.status(401).body(
                Map.of("error", "Unauthorized", "message", "Not logged in"));
        }

        Optional<UserSession> sessionOpt = userAuthService.findSession(sessionId);
        if (sessionOpt.isEmpty() || sessionOpt.get().getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            sessionOpt.ifPresent(s -> userAuthService.deleteSession(s.getId()));
            return ResponseEntity.status(401).body(
                Map.of("error", "Unauthorized", "message", "Session expired or invalid"));
        }

        UserSession session = sessionOpt.get();
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("username", session.getUsername());
        response.put("userId", session.getUserId());

        Optional<AppUser> userOpt = userAuthService.findById(session.getUserId());
        userOpt.ifPresent(user -> {
            response.put("email", user.getEmail());
            response.put("storageUsedKb", user.getStorageUsedKb());
            response.put("plan", user.getSubscriptionPlanId());
            response.put("customPlanPending", user.isCustomPlanPending());
            subscriptionPlanRepository.findById(user.getSubscriptionPlanId()).ifPresent(plan -> {
                response.put("planLimitMb", plan.getStorageLimitMb());
                response.put("planPrice", plan.getPrice());
            });
        });

        Optional<ApiCredential> credOpt = userAuthService.getApiCredentials(session.getUserId());
        if (credOpt.isPresent()) {
            response.put("clientId", credOpt.get().getClientId());
            response.put("clientSecret", credOpt.get().getClientSecret());
            response.put("scope", credOpt.get().getScope());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = "session", required = false) String sessionId) {
        if (sessionId != null) {
            userAuthService.deleteSession(sessionId);
        }

        ResponseCookie cookie = ResponseCookie.from("session", "")
            .httpOnly(true)
            .sameSite("Lax")
            .maxAge(0)
            .path("/")
            .build();

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(Map.of("status", "SUCCESS", "message", "Logged out"));
    }
}
