package com.example.cloudstorage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_credentials")
public class ApiCredential {

    @Id
    private String clientId;

    @Column(nullable = false)
    private String clientSecret;

    @Column(nullable = false)
    private String scope;

    @Column(nullable = false)
    private String appName;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public ApiCredential() {}

    public ApiCredential(String clientId, String clientSecret, String scope, String appName, String userId, LocalDateTime createdAt) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.appName = appName;
        this.userId = userId;
        this.active = true;
        this.createdAt = createdAt;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
