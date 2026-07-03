package com.example.cloudstorage.client;

import com.example.cloudstorage.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class CloudStorageClient {

    private static final Logger log = LoggerFactory.getLogger(CloudStorageClient.class);

    private final RestTemplate restTemplate;
    private final ApiClientConfig config;

    public CloudStorageClient(RestTemplate restTemplate, ApiClientConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    // ──────────────────────────────────────────────
    //  AUTHENTICATION
    // ──────────────────────────────────────────────

    public Map<String, Object> signup(String email, String password) {
        var body = Map.of("email", email, "password", password);
        return post("/api/v1/auth/signup", body);
    }

    public VerifyResponse verify(String token) {
        var body = Map.of("token", token);
        var resp = restTemplate.exchange(
                config.getBaseUrl() + "/api/v1/auth/verify",
                HttpMethod.POST, new HttpEntity<>(body), VerifyResponse.class);
        return resp.getBody();
    }

    public ResponseEntity<Map<String, Object>> login(String email, String password) {
        var body = Map.of("email", email, "password", password);
        return restTemplate.exchange(
                config.getBaseUrl() + "/api/v1/auth/login",
                HttpMethod.POST, new HttpEntity<>(body),
                new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    public Map<String, Object> me(String sessionCookie) {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "session=" + sessionCookie);
        return get("/api/v1/auth/me", headers);
    }

    public Map<String, Object> logout(String sessionCookie) {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "session=" + sessionCookie);
        return post("/api/v1/auth/logout", Map.of(), headers);
    }

    // ──────────────────────────────────────────────
    //  API CREDENTIALS (Client Auth)
    // ──────────────────────────────────────────────

    public Map<String, Object> registerClient(String clientId, String clientSecret, String scope, String appName) {
        var body = Map.of("clientId", clientId, "clientSecret", clientSecret, "scope", scope, "appName", appName);
        return post("/api/v1/auth/register", body);
    }

    public TokenResponse requestToken(String clientId, String clientSecret) {
        var body = Map.of("clientId", clientId, "clientSecret", clientSecret);
        var resp = restTemplate.exchange(
                config.getBaseUrl() + "/api/v1/auth/token",
                HttpMethod.POST, new HttpEntity<>(body), TokenResponse.class);
        return resp.getBody();
    }

    // ──────────────────────────────────────────────
    //  SUBSCRIPTION PLANS
    // ──────────────────────────────────────────────

    public List<Map<String, Object>> listPlans() {
        var resp = restTemplate.exchange(
                config.getBaseUrl() + "/api/v1/plans",
                HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        return resp.getBody();
    }

    public Map<String, Object> assignPlan(String email, String planId) {
        return post("/api/v1/plans/assign", Map.of("email", email, "planId", planId));
    }

    public Map<String, Object> selectPlan(String sessionCookie, String planId) {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "session=" + sessionCookie);
        return post("/api/v1/plans/select", Map.of("planId", planId), headers);
    }

    public Map<String, Object> createCheckoutSession(String email, String planId) {
        return post("/api/v1/plans/create-checkout", Map.of("email", email, "planId", planId));
    }

    public Map<String, Object> createCheckoutSession(String email, String planId, long diffCents) {
        return post("/api/v1/plans/create-checkout",
                Map.of("email", email, "planId", planId, "diffCents", String.valueOf(diffCents)));
    }

    public String getCheckoutSuccessHtml(String sessionId, String email, String planId) {
        var url = config.getBaseUrl() + "/api/v1/plans/checkout-success" +
                "?session_id=" + sessionId + "&email=" + email + "&planId=" + planId;
        var resp = restTemplate.getForEntity(url, String.class);
        return resp.getBody();
    }

    public Map<String, Object> customPlanRequest(String sessionCookie, String text) {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "session=" + sessionCookie);
        return post("/api/v1/plans/custom-request", Map.of("text", text), headers);
    }

    // ──────────────────────────────────────────────
    //  STORAGE
    // ──────────────────────────────────────────────

    public List<FileResponse> listFiles(String bearerToken) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        var resp = restTemplate.exchange(
                config.getBaseUrl() + "/api/v1/storage/files",
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<FileResponse>>() {});
        return resp.getBody();
    }

    public FileResponse uploadFile(String bearerToken, String name, String contentType, long size, String base64Data) {
        var body = Map.of("name", name, "type", contentType, "size", size, "data", base64Data);
        var headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        var resp = restTemplate.exchange(
                config.getBaseUrl() + "/api/v1/storage/upload",
                HttpMethod.POST, new HttpEntity<>(body, headers), FileResponse.class);
        return resp.getBody();
    }

    public FileResponse uploadMultipart(String bearerToken, String name, String contentType, byte[] data) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        var resource = new HttpEntity<>(data, createHeaders(contentType));
        body.add("file", resource);

        var resp = restTemplate.exchange(
                config.getBaseUrl() + "/api/v1/storage/upload/multipart",
                HttpMethod.POST, new HttpEntity<>(body, headers), FileResponse.class);
        return resp.getBody();
    }

    public byte[] downloadFile(String bearerToken, String fileId) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        var resp = restTemplate.exchange(
                config.getBaseUrl() + "/api/v1/storage/files/" + fileId,
                HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        return resp.getBody();
    }

    public Map<String, Object> deleteFile(String bearerToken, String fileId) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        var resp = restTemplate.exchange(
                config.getBaseUrl() + "/api/v1/storage/files/" + fileId,
                HttpMethod.DELETE, new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        return resp.getBody();
    }

    // ──────────────────────────────────────────────
    //  INTERNAL
    // ──────────────────────────────────────────────

    public DebugStateResponse debugState() {
        var resp = restTemplate.getForEntity(
                config.getBaseUrl() + "/api/internal/debug-state", DebugStateResponse.class);
        return resp.getBody();
    }

    // ──────────────────────────────────────────────
    //  HELPERS
    // ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body) {
        return post(path, body, new HttpHeaders());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resp = restTemplate.exchange(
                config.getBaseUrl() + path,
                HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        return resp.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path, HttpHeaders headers) {
        var resp = restTemplate.exchange(
                config.getBaseUrl() + path,
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        return resp.getBody();
    }

    private HttpHeaders createHeaders(String contentType) {
        var h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(contentType));
        return h;
    }
}
