package com.picsforyou.cloud.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthResponse {

    private String status;
    private String message;
    private String token;
    private String email;
    private String plan;
    private Long planLimitMb;
    private Double planPrice;
    private Long storageUsedKb;
    @JsonProperty("customPlanPending") private boolean customPlanPending;
    private String clientId;
    private String clientSecret;
    private String scope;

    public AuthResponse() {}

    public static AuthResponse fromMap(Map<String, Object> map) {
        AuthResponse r = new AuthResponse();
        r.status = (String) map.get("status");
        r.message = (String) map.get("message");
        r.token = (String) map.get("token");
        r.email = (String) map.get("email");
        r.plan = (String) map.get("plan");
        r.customPlanPending = Boolean.TRUE.equals(map.get("customPlanPending"));
        if (map.get("planLimitMb") instanceof Number n) r.planLimitMb = n.longValue();
        if (map.get("planPrice") instanceof Number n) r.planPrice = n.doubleValue();
        if (map.get("storageUsedKb") instanceof Number n) r.storageUsedKb = n.longValue();
        r.clientId = (String) map.get("clientId");
        r.clientSecret = (String) map.get("clientSecret");
        r.scope = (String) map.get("scope");
        return r;
    }

    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getToken() { return token; }
    public String getEmail() { return email; }
    public String getPlan() { return plan; }
    public Long getPlanLimitMb() { return planLimitMb; }
    public Double getPlanPrice() { return planPrice; }
    public Long getStorageUsedKb() { return storageUsedKb; }
    public boolean isCustomPlanPending() { return customPlanPending; }
    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getScope() { return scope; }

    public boolean isSuccess() {
        return "VERIFIED".equals(status) || "LOGGED_IN".equals(status) || "REGISTERED".equals(status);
    }
}
