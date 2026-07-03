package com.example.cloudstorage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    private String id;

    @Column(nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean emailVerified;

    @Column
    private String verificationToken;

    @Column
    private LocalDateTime verificationTokenExpiry;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private long storageUsedKb;

    @Column(nullable = false)
    private String subscriptionPlanId;

    @Column
    private String stripePaymentIntentId;

    @Column
    private Long stripeAmountPaid; // in cents

    @Column
    private String paypalOrderId;

    @Column
    private String paypalCaptureId;

    @Column
    private Long paypalAmountPaid; // in cents

    @Column
    private String stripeSubscriptionId;

    @Column
    private String paypalSubscriptionId;

    @Column(nullable = false)
    private boolean customPlanPending;

    public AppUser() {}

    public AppUser(String id, String username, String email, String passwordHash, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.emailVerified = false;
        this.createdAt = createdAt;
        this.storageUsedKb = 0;
        this.subscriptionPlanId = "free";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }
    public LocalDateTime getVerificationTokenExpiry() { return verificationTokenExpiry; }
    public void setVerificationTokenExpiry(LocalDateTime verificationTokenExpiry) { this.verificationTokenExpiry = verificationTokenExpiry; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public long getStorageUsedKb() { return storageUsedKb; }
    public void setStorageUsedKb(long storageUsedKb) { this.storageUsedKb = storageUsedKb; }
    public void addStorageKb(long kb) { this.storageUsedKb += kb; }
    public String getSubscriptionPlanId() { return subscriptionPlanId; }
    public void setSubscriptionPlanId(String subscriptionPlanId) { this.subscriptionPlanId = subscriptionPlanId; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public void setStripePaymentIntentId(String stripePaymentIntentId) { this.stripePaymentIntentId = stripePaymentIntentId; }
    public Long getStripeAmountPaid() { return stripeAmountPaid; }
    public void setStripeAmountPaid(Long stripeAmountPaid) { this.stripeAmountPaid = stripeAmountPaid; }
    public String getPaypalOrderId() { return paypalOrderId; }
    public void setPaypalOrderId(String paypalOrderId) { this.paypalOrderId = paypalOrderId; }
    public String getPaypalCaptureId() { return paypalCaptureId; }
    public void setPaypalCaptureId(String paypalCaptureId) { this.paypalCaptureId = paypalCaptureId; }
    public Long getPaypalAmountPaid() { return paypalAmountPaid; }
    public void setPaypalAmountPaid(Long paypalAmountPaid) { this.paypalAmountPaid = paypalAmountPaid; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }
    public String getPaypalSubscriptionId() { return paypalSubscriptionId; }
    public void setPaypalSubscriptionId(String paypalSubscriptionId) { this.paypalSubscriptionId = paypalSubscriptionId; }
    public boolean isCustomPlanPending() { return customPlanPending; }
    public void setCustomPlanPending(boolean customPlanPending) { this.customPlanPending = customPlanPending; }
}
