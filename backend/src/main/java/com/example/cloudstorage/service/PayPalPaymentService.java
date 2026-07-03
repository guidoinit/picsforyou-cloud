package com.example.cloudstorage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@Service
public class PayPalPaymentService {

    private static final Logger log = LoggerFactory.getLogger(PayPalPaymentService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PayPalPaymentService() {
        RestTemplate template = new RestTemplate();
        try {
            template.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        } catch (NoClassDefFoundError e) {
            log.warn("Apache HttpClient not available, falling back to default RestTemplate");
        }
        this.restTemplate = template;
    }

    @Value("${paypal.client-id}")
    private String clientId;

    @Value("${paypal.client-secret}")
    private String clientSecret;

    @Value("${paypal.mode:sandbox}")
    private String mode;

    private String getBaseUrl() {
        return "sandbox".equalsIgnoreCase(mode)
            ? "https://api-m.sandbox.paypal.com"
            : "https://api-m.paypal.com";
    }

    private String getAccessToken() {
        try {
            String auth = clientId + ":" + clientSecret;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Authorization", "Basic " + encodedAuth);

            String body = "grant_type=client_credentials";
            HttpEntity<String> request = new HttpEntity<>(body, headers);

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                getBaseUrl() + "/v1/oauth2/token",
                request,
                JsonNode.class
            );

            String token = response.getBody().get("access_token").asText();
            return token;
        } catch (Exception e) {
            log.error("Failed to get PayPal access token", e);
            throw new RuntimeException("PayPal authentication failed: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> createSubscription(String email, String planId, long amountCents) {
        try {
            String accessToken = getAccessToken();
            String planName = switch (planId) {
                case "base" -> "Base";
                case "professional" -> "Professional";
                default -> throw new IllegalArgumentException("Invalid paid plan: " + planId);
            };

            String amountStr = String.format(Locale.US, "%.2f", amountCents / 100.0);

            // 1. Create a billing plan
            String planIdStr = createBillingPlanV1(accessToken, planName, amountStr);

            // 2. Create a billing agreement (subscription)
            ObjectNode agreement = objectMapper.createObjectNode();
            agreement.put("name", planName + " Monthly Subscription");
            agreement.put("description", "picsforyou.cloud " + planName.toLowerCase() + " monthly subscription");
            agreement.put("start_date", java.time.Instant.now().plus(java.time.Duration.ofDays(1)).toString().replace("Z", "") + "Z");

            ObjectNode plan = objectMapper.createObjectNode();
            plan.put("id", planIdStr);
            agreement.set("plan", plan);

            ObjectNode payer = objectMapper.createObjectNode();
            payer.put("payment_method", "paypal");
            agreement.set("payer", payer);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);

            String agreementJson = objectMapper.writeValueAsString(agreement);
            log.info("Creating billing agreement with JSON: {}", agreementJson);
            HttpEntity<String> request = new HttpEntity<>(agreementJson, headers);

            ResponseEntity<String> agreementResponse = restTemplate.postForEntity(
                getBaseUrl() + "/v1/payments/billing-agreements",
                request,
                String.class
            );

            log.info("Billing agreement response ({}): {}", agreementResponse.getStatusCode(), agreementResponse.getBody());

            JsonNode body = objectMapper.readTree(agreementResponse.getBody());
            String approvalUrl = "";
            String agreementToken = "";
            ArrayNode links = (ArrayNode) body.get("links");
            for (JsonNode link : links) {
                String rel = link.get("rel").asText();
                if ("approval_url".equals(rel)) {
                    approvalUrl = link.get("href").asText();
                } else if ("execute".equals(rel)) {
                    String executeUrl = link.get("href").asText();
                    agreementToken = executeUrl.replaceAll(".*/billing-agreements/([^/]+)/agreement-execute.*", "$1");
                }
            }
            if (approvalUrl.isBlank() || agreementToken.isBlank()) {
                log.error("PayPal billing agreement response missing approval_url or execute link: {}", body.toPrettyString());
                throw new RuntimeException("PayPal billing agreement creation failed: missing links");
            }

            log.info("PayPal subscription agreement created, token: {} (plan: {})", agreementToken, planIdStr);

            return Map.of(
                "agreementId", agreementToken,
                "approvalUrl", approvalUrl,
                "amount", amountCents,
                "currency", "EUR"
            );
        } catch (HttpStatusCodeException e) {
            log.error("Failed to create PayPal subscription. Status: {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("PayPal subscription creation failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to create PayPal subscription", e);
            throw new RuntimeException("PayPal subscription creation failed: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> executeAgreement(String token) {
        try {
            String accessToken = getAccessToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);

            HttpEntity<String> request = new HttpEntity<>("", headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/v1/payments/billing-agreements/" + token + "/agreement-execute",
                request,
                String.class
            );

            log.info("PayPal agreement execution response ({}): {}", response.getStatusCode(), response.getBody());

            JsonNode body = objectMapper.readTree(response.getBody());
            String agreementId = body.get("id").asText();
            String status = body.get("state").asText();

            log.info("PayPal agreement executed: {} (status: {})", agreementId, status);

            long amountCents = 0;
            try {
                JsonNode plan = body.get("plan");
                JsonNode paymentDefinitions = plan.get("payment_definitions");
                if (paymentDefinitions != null && paymentDefinitions.isArray() && paymentDefinitions.size() > 0) {
                    String value = paymentDefinitions.get(0).get("amount").get("value").asText();
                    amountCents = (long) (Double.parseDouble(value) * 100);
                }
            } catch (Exception ignored) {}

            return Map.of(
                "agreementId", agreementId,
                "status", status,
                "amount", amountCents
            );
        } catch (HttpStatusCodeException e) {
            log.error("Failed to execute PayPal agreement. Status: {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("PayPal agreement execution failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Failed to execute PayPal agreement", e);
            throw new RuntimeException("PayPal agreement execution failed: " + e.getMessage(), e);
        }
    }

    private String createBillingPlanV1(String accessToken, String planName, String amountStr) {
        try {
            ObjectNode plan = objectMapper.createObjectNode();
            plan.put("name", planName + " Monthly Plan");
            plan.put("description", planName + " monthly recurring plan for picsforyou.cloud");
            plan.put("type", "INFINITE");

            ArrayNode paymentDefinitions = objectMapper.createArrayNode();
            ObjectNode paymentDef = objectMapper.createObjectNode();
            paymentDef.put("name", planName + " Monthly Payment");
            paymentDef.put("type", "REGULAR");
            paymentDef.put("frequency", "MONTH");
            paymentDef.put("frequency_interval", "1");
            paymentDef.put("cycles", "0");
            paymentDef.put("amount", objectMapper.createObjectNode()
                .put("currency", "EUR")
                .put("value", amountStr));

            ArrayNode chargeModels = objectMapper.createArrayNode();
            ObjectNode chargeModel = objectMapper.createObjectNode();
            chargeModel.put("type", "TAX");
            chargeModel.put("amount", objectMapper.createObjectNode()
                .put("currency", "EUR")
                .put("value", "0"));
            chargeModels.add(chargeModel);
            paymentDef.set("charge_models", chargeModels);
            paymentDefinitions.add(paymentDef);
            plan.set("payment_definitions", paymentDefinitions);

            ObjectNode merchantPreferences = objectMapper.createObjectNode();
            merchantPreferences.put("setup_fee", objectMapper.createObjectNode()
                .put("currency", "EUR")
                .put("value", "0"));
            merchantPreferences.put("return_url", "http://localhost:3000/api/v1/plans/paypal-subscription-success");
            merchantPreferences.put("cancel_url", "http://localhost:5173");
            merchantPreferences.put("auto_bill_amount", "YES");
            merchantPreferences.put("initial_fail_amount_action", "CONTINUE");
            merchantPreferences.put("max_fail_attempts", "3");
            plan.set("merchant_preferences", merchantPreferences);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);

            String planJson = objectMapper.writeValueAsString(plan);
            log.info("Creating billing plan with JSON: {}", planJson);
            HttpEntity<String> request = new HttpEntity<>(planJson, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/v1/payments/billing-plans",
                request,
                String.class
            );

            log.info("Billing plan create response ({}): {}", response.getStatusCode(), response.getBody());

            JsonNode body = objectMapper.readTree(response.getBody());
            if (body == null || !body.has("id")) {
                String errMsg = body != null ? body.toPrettyString() : "null body";
                log.error("PayPal billing plan creation failed: {}", errMsg);
                throw new RuntimeException("PayPal billing plan creation failed: " + errMsg);
            }

            String planIdStr = body.get("id").asText();

            // Activate the plan
            ArrayNode patches = objectMapper.createArrayNode();
            ObjectNode patchItem = objectMapper.createObjectNode();
            patchItem.put("op", "replace");
            patchItem.put("path", "/");
            ObjectNode patchValue = objectMapper.createObjectNode();
            patchValue.put("state", "ACTIVE");
            patchItem.set("value", patchValue);
            patches.add(patchItem);

            HttpEntity<String> patchRequest = new HttpEntity<>(objectMapper.writeValueAsString(patches), headers);
            log.info("Activating billing plan {} with PATCH: {}", planIdStr, objectMapper.writeValueAsString(patches));
            ResponseEntity<String> activateResponse = restTemplate.exchange(
                getBaseUrl() + "/v1/payments/billing-plans/" + planIdStr,
                HttpMethod.PATCH,
                patchRequest,
                String.class
            );
            log.info("Plan activation response: {} {}", activateResponse.getStatusCode(), activateResponse.getBody());

            log.info("PayPal billing plan created and activated: {}", planIdStr);
            return planIdStr;
        } catch (HttpStatusCodeException e) {
            log.error("PayPal billing plan creation failed. Status: {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("PayPal billing plan creation failed (" + e.getStatusCode() + "): " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("PayPal billing plan creation failed", e);
            throw new RuntimeException("PayPal billing plan creation failed: " + e.getMessage(), e);
        }
    }

    public boolean cancelSubscription(String agreementId) {
        try {
            String accessToken = getAccessToken();

            ObjectNode cancelRequest = objectMapper.createObjectNode();
            cancelRequest.put("note", "Subscription cancelled by user");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + accessToken);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(cancelRequest), headers);

            restTemplate.postForEntity(
                getBaseUrl() + "/v1/payments/billing-agreements/" + agreementId + "/cancel",
                request,
                String.class
            );

            log.info("PayPal subscription cancelled: {}", agreementId);
            return true;
        } catch (Exception e) {
            log.error("Failed to cancel PayPal subscription", e);
            return false;
        }
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
            && clientSecret != null && !clientSecret.isBlank();
    }

    public String getClientId() {
        return clientId;
    }
}
