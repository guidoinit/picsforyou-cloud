package com.example.cloudstorage;

import com.example.cloudstorage.model.ActiveToken;
import com.example.cloudstorage.model.ApiCredential;
import com.example.cloudstorage.model.AppUser;
import com.example.cloudstorage.model.BucketFile;
import com.example.cloudstorage.model.ClientCredential;
import com.example.cloudstorage.repository.ActiveTokenRepository;
import com.example.cloudstorage.repository.ApiCredentialRepository;
import com.example.cloudstorage.repository.AppUserRepository;
import com.example.cloudstorage.repository.BucketFileRepository;
import com.example.cloudstorage.repository.ClientCredentialRepository;
import com.example.cloudstorage.model.SubscriptionPlan;
import com.example.cloudstorage.repository.SubscriptionPlanRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AppUserRepository appUserRepository;
    private final ClientCredentialRepository clientCredentialRepository;
    private final ActiveTokenRepository activeTokenRepository;
    private final BucketFileRepository bucketFileRepository;
    private final ApiCredentialRepository apiCredentialRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PasswordEncoder passwordEncoder;
    private String adminUserId;

    public DataInitializer(AppUserRepository appUserRepository,
                           ClientCredentialRepository clientCredentialRepository,
                           ActiveTokenRepository activeTokenRepository,
                           BucketFileRepository bucketFileRepository,
                           ApiCredentialRepository apiCredentialRepository,
                           SubscriptionPlanRepository subscriptionPlanRepository,
                           PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.clientCredentialRepository = clientCredentialRepository;
        this.activeTokenRepository = activeTokenRepository;
        this.bucketFileRepository = bucketFileRepository;
        this.apiCredentialRepository = apiCredentialRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedSubscriptionPlans();

        if (appUserRepository.existsByEmail("admin@example.com")) {
            return;
        }

        seedAdminUser();
        seedClientCredential();
        seedPreauthorizedToken();
        seedFiles();
    }

    private void seedSubscriptionPlans() {
        if (subscriptionPlanRepository.existsById("free")) return;

        subscriptionPlanRepository.save(new SubscriptionPlan(
            "free", "Free", 0, 30,
            "Free plan with 30 MB storage limit. Delete files to free up space."));
        subscriptionPlanRepository.save(new SubscriptionPlan(
            "base", "Base", 5.99, 5120,
            "5 GB storage for €5.99/month. Perfect for personal use."));
        subscriptionPlanRepository.save(new SubscriptionPlan(
            "professional", "Professional", 20.00, 20480,
            "20 GB storage for €20.00/month. For professionals."));
        subscriptionPlanRepository.save(new SubscriptionPlan(
            "custom", "Custom", -1, -1,
            "Custom plan. Contact the platform for personalized storage solutions."));
    }

    private void seedAdminUser() {
        adminUserId = "user_" + UUID.randomUUID().toString().substring(0, 8);
        AppUser admin = new AppUser(
            adminUserId, "admin", "admin@example.com",
            passwordEncoder.encode("admin"), LocalDateTime.now()
        );
        admin.setEmailVerified(true);
        admin.setSubscriptionPlanId("professional");
        appUserRepository.save(admin);

        ApiCredential cred = new ApiCredential(
            "admin-client-id",
            "admin-client-secret",
            "FULL_ACCESS",
            "admin-api-key",
            adminUserId,
            LocalDateTime.now()
        );
        apiCredentialRepository.save(cred);

        ApiCredential devCred = new ApiCredential(
            "dev-client-id",
            "dev-client-secret-xyz123",
            "FULL_ACCESS",
            "DevPortal-Gateway",
            adminUserId,
            LocalDateTime.now()
        );
        apiCredentialRepository.save(devCred);
    }

    private void seedClientCredential() {
        ClientCredential cred = new ClientCredential(
            "dev-client-id",
            "dev-client-secret-xyz123",
            "FULL_ACCESS",
            "DevPortal-Gateway"
        );
        clientCredentialRepository.save(cred);
    }

    private void seedPreauthorizedToken() {
        ActiveToken token = new ActiveToken(
            "dev-bucket-access-token-9982",
            "FULL_ACCESS",
            "DevPortal-Gateway",
            LocalDateTime.now().plusYears(1),
            adminUserId
        );
        activeTokenRepository.save(token);
    }

    private void seedFiles() {
        String dashSvg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 600" width="100%" height="100%">
              <defs>
                <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#0f172a"/>
                  <stop offset="100%" stop-color="#1e1b4b"/>
                </linearGradient>
                <linearGradient id="grid-grad" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" stop-color="#4f46e5" stop-opacity="0.2"/>
                  <stop offset="100%" stop-color="#06b6d4" stop-opacity="0"/>
                </linearGradient>
              </defs>
              <rect width="800" height="600" fill="url(#bg)"/>
              <path d="M 0,100 L 800,100 M 0,200 L 800,200 M 0,300 L 800,300 M 0,400 L 800,400 M 0,500 L 800,500 M 100,0 L 100,600 M 200,0 L 200,600 M 300,0 L 300,600 M 400,0 L 400,600 M 500,0 L 500,600 M 600,0 L 600,600 M 700,0 L 700,600" stroke="url(#grid-grad)" stroke-width="1"/>
              <circle cx="400" cy="300" r="120" fill="none" stroke="#6366f1" stroke-width="2" stroke-dasharray="10 5"/>
              <circle cx="400" cy="300" r="80" fill="none" stroke="#22d3ee" stroke-width="4"/>
              <circle cx="400" cy="300" r="10" fill="#38bdf8"/>
              <text x="400" y="470" text-anchor="middle" fill="#38bdf8" font-family="monospace" font-size="22" font-weight="bold">JAVA GCS BACKEND BUCKET</text>
              <text x="400" y="505" text-anchor="middle" fill="#64748b" font-family="sans-serif" font-size="14">Default Seeded Asset: cloud-storage-dashboard.svg</text>
            </svg>""";

        String logoSvg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 400" width="100%" height="100%">
              <defs>
                <linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stop-color="#f97316"/>
                  <stop offset="100%" stop-color="#ec4899"/>
                </linearGradient>
              </defs>
              <rect width="400" height="400" rx="40" fill="url(#grad)"/>
              <circle cx="200" cy="200" r="90" fill="#ffffff" fill-opacity="0.15"/>
              <path d="M 150,150 L 250,150 L 200,250 Z" fill="#ffffff"/>
              <text x="200" y="320" text-anchor="middle" fill="#ffffff" font-family="sans-serif" font-size="18" font-weight="bold">CLOUD BUCKET EXPLORER</text>
            </svg>""";

        String successSvg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 400" width="100%" height="100%">
              <rect width="600" height="400" fill="#020617"/>
              <rect x="50" y="50" width="500" height="300" rx="8" fill="#0f172a" stroke="#1e293b" stroke-width="2"/>
              <circle cx="300" cy="160" r="40" fill="#22c55e" fill-opacity="0.2"/>
              <path d="M 285,160 L 297,172 L 320,145" fill="none" stroke="#22c55e" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
              <text x="300" y="240" text-anchor="middle" fill="#ffffff" font-family="sans-serif" font-size="18" font-weight="bold">Connection Authorized</text>
              <text x="300" y="270" text-anchor="middle" fill="#64748b" font-family="sans-serif" font-size="13">Java Spring Boot Integration: Successful</text>
            </svg>""";

        saveFile("cloud-storage-dashboard", "cloud-storage-dashboard.svg", dashSvg, -7200000L);
        saveFile("java-cloud-logo", "java-cloud-logo.svg", logoSvg, -1800000L);
        saveFile("auth-success-badge", "auth-success-badge.svg", successSvg, -300000L);
    }

    private void saveFile(String id, String name, String svgContent, long uploadedAgoMillis) {
        byte[] bytes = svgContent.getBytes(StandardCharsets.UTF_8);

        BucketFile file = new BucketFile(
            id,
            name,
            "image/svg+xml",
            (long) bytes.length,
            LocalDateTime.now().plusNanos(uploadedAgoMillis * 1_000_000L),
            "DevPortal-Gateway",
            null,
            null
        );
        bucketFileRepository.save(file);

        try {
            java.nio.file.Files.write(java.nio.file.Paths.get("./storage/" + id), bytes);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to write seed file: " + id, e);
        }
    }
}
