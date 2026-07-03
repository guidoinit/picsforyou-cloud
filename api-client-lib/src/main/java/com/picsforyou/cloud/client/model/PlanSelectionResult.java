package com.picsforyou.cloud.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanSelectionResult {

    private String status;
    private String planId;
    private String action;
    private long diffCents;
    private String message;
    private String url;
    private String sessionId;
    private long amount;
    private String currency;
    private String email;

    public PlanSelectionResult() {}

    public static PlanSelectionResult fromMap(Map<String, Object> map) {
        PlanSelectionResult r = new PlanSelectionResult();
        r.status = (String) map.get("status");
        r.planId = (String) map.get("planId");
        r.action = (String) map.get("action");
        r.message = (String) map.get("message");
        r.url = (String) map.get("url");
        r.sessionId = (String) map.get("sessionId");
        r.currency = (String) map.get("currency");
        r.email = (String) map.get("email");
        if (map.get("diffCents") instanceof Number n) r.diffCents = n.longValue();
        if (map.get("amount") instanceof Number n) r.amount = n.longValue();
        return r;
    }

    public boolean requiresUpgrade() { return "UPGRADE_REQUIRED".equals(action); }
    public boolean isDowngrade() { return "DOWNGRADE_OK".equals(status); }
    public boolean isStripeRedirect() { return url != null && !url.isEmpty(); }

    public String getStatus() { return status; }
    public String getPlanId() { return planId; }
    public String getAction() { return action; }
    public long getDiffCents() { return diffCents; }
    public String getMessage() { return message; }
    public String getUrl() { return url; }
    public String getSessionId() { return sessionId; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getEmail() { return email; }
}
