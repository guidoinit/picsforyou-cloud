package com.example.cloudstorage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "active_tokens")
public class ActiveToken {

    @Id
    private String token;

    @Column(nullable = false)
    private String scope;

    @Column(nullable = false)
    private String appName;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column
    private String userId;

    public ActiveToken() {}

    public ActiveToken(String token, String scope, String appName, LocalDateTime expiresAt, String userId) {
        this.token = token;
        this.scope = scope;
        this.appName = appName;
        this.expiresAt = expiresAt;
        this.userId = userId;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
