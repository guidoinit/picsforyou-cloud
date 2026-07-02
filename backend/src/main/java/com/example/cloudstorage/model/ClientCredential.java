package com.example.cloudstorage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "client_credentials")
public class ClientCredential {

    @Id
    private String clientId;

    @Column(nullable = false)
    private String clientSecret;

    @Column(nullable = false)
    private String scope;

    private String appName;

    public ClientCredential() {}

    public ClientCredential(String clientId, String clientSecret, String scope, String appName) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.appName = appName;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
}
