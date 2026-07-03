package com.picsforyou.cloud.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.picsforyou.cloud.client.model.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PicsForYouCloudClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private String sessionCookie;

    public PicsForYouCloudClient() {
        this("http://localhost:3000");
    }

    public PicsForYouCloudClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper();
    }

    public void setSessionCookie(String cookie) { this.sessionCookie = cookie; }
    public String getSessionCookie() { return sessionCookie; }

    // ──────────────────────────────────────────────
    //  AUTHENTICATION
    // ──────────────────────────────────────────────

    public AuthResponse signup(String email, String password) throws ApiException {
        return AuthResponse.fromMap(post("/api/v1/auth/signup", Map.of("email", email, "password", password)));
    }

    public AuthResponse verify(String token) throws ApiException {
        return AuthResponse.fromMap(post("/api/v1/auth/verify", Map.of("token", token)));
    }

    public AuthResponse login(String email, String password) throws ApiException {
        var resp = postWithHeaders("/api/v1/auth/login", Map.of("email", email, "password", password), Map.of());
        var cookies = resp.headers().firstValue("Set-Cookie").orElse("");
        if (cookies.contains("session=")) {
            var session = cookies.replaceAll(".*session=([^;]+).*", "$1");
            setSessionCookie(session);
        }
        return AuthResponse.fromMap(parseBody(resp.body()));
    }

    public AuthResponse me() throws ApiException {
        return AuthResponse.fromMap(get("/api/v1/auth/me"));
    }

    public Map<String, Object> logout() throws ApiException {
        return post("/api/v1/auth/logout", Map.of());
    }

    // ──────────────────────────────────────────────
    //  API CREDENTIALS
    // ──────────────────────────────────────────────

    public Map<String, Object> registerClient(String clientId, String clientSecret, String scope, String appName)
            throws ApiException {
        return post("/api/v1/auth/register", Map.of(
                "clientId", clientId, "clientSecret", clientSecret,
                "scope", scope, "appName", appName));
    }

    public TokenResponse requestToken(String clientId, String clientSecret) throws ApiException {
        var body = post("/api/v1/auth/token", Map.of("clientId", clientId, "clientSecret", clientSecret));
        return mapper.convertValue(body, TokenResponse.class);
    }

    // ──────────────────────────────────────────────
    //  PLANS
    // ──────────────────────────────────────────────

    public List<Plan> listPlans() throws ApiException {
        var raw = get("/api/v1/plans");
        return mapper.convertValue(raw, new TypeReference<List<Plan>>() {});
    }

    public PlanSelectionResult assignPlan(String email, String planId) throws ApiException {
        return PlanSelectionResult.fromMap(
                post("/api/v1/plans/assign", Map.of("email", email, "planId", planId)));
    }

    public PlanSelectionResult selectPlan(String planId) throws ApiException {
        return PlanSelectionResult.fromMap(
                post("/api/v1/plans/select", Map.of("planId", planId)));
    }

    public PlanSelectionResult createCheckoutSession(String email, String planId) throws ApiException {
        return PlanSelectionResult.fromMap(
                post("/api/v1/plans/create-checkout", Map.of("email", email, "planId", planId)));
    }

    public PlanSelectionResult createCheckoutSession(String email, String planId, long diffCents) throws ApiException {
        return PlanSelectionResult.fromMap(
                post("/api/v1/plans/create-checkout",
                        Map.of("email", email, "planId", planId, "diffCents", String.valueOf(diffCents))));
    }

    public String getCheckoutSuccessHtml(String sessionId, String email, String planId) throws ApiException {
        var uri = baseUrl + "/api/v1/plans/checkout-success?session_id=" + sessionId
                + "&email=" + email + "&planId=" + planId;
        try {
            var req = HttpRequest.newBuilder(URI.create(uri)).GET().build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (IOException | InterruptedException e) {
            throw new ApiException("Failed to fetch checkout success page", e);
        }
    }

    public Map<String, Object> customPlanRequest(String text) throws ApiException {
        return post("/api/v1/plans/custom-request", Map.of("text", text));
    }

    // ──────────────────────────────────────────────
    //  STORAGE (Bearer Token Auth)
    // ──────────────────────────────────────────────

    public List<UploadedFile> listFiles(String bearerToken) throws ApiException {
        var raw = get("/api/v1/storage/files", bearerToken);
        return mapper.convertValue(raw, new TypeReference<List<UploadedFile>>() {});
    }

    public UploadedFile uploadFile(String bearerToken, String name, String contentType,
                                   long size, String base64Data) throws ApiException {
        var raw = post("/api/v1/storage/upload", Map.of(
                "name", name, "type", contentType, "size", size, "data", base64Data), bearerToken);
        return mapper.convertValue(raw, UploadedFile.class);
    }

    public byte[] downloadFile(String bearerToken, String fileId) throws ApiException {
        try {
            var req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/storage/files/" + fileId))
                    .header("Authorization", "Bearer " + bearerToken)
                    .GET().build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 400) {
                throw new ApiException("Download failed: HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (IOException | InterruptedException e) {
            throw new ApiException("Failed to download file", e);
        }
    }

    public Map<String, Object> deleteFile(String bearerToken, String fileId) throws ApiException {
        return delete("/api/v1/storage/files/" + fileId, bearerToken);
    }

    // ──────────────────────────────────────────────
    //  INTERNAL / DEBUG
    // ──────────────────────────────────────────────

    public Map<String, Object> debugState() throws ApiException {
        return get("/api/internal/debug-state");
    }

    // ──────────────────────────────────────────────
    //  HTTP HELPERS
    // ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body) throws ApiException {
        var resp = postWithHeaders(path, body, buildHeaders());
        return parseBody(resp.body());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body, String bearerToken) throws ApiException {
        var headers = new HashMap<>(buildHeaders());
        headers.put("Authorization", "Bearer " + bearerToken);
        var resp = postWithHeaders(path, body, headers);
        return parseBody(resp.body());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path) throws ApiException {
        return get(path, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path, String bearerToken) throws ApiException {
        try {
            var reqBuilder = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
            if (bearerToken != null) reqBuilder.header("Authorization", "Bearer " + bearerToken);
            buildHeaders().forEach(reqBuilder::header);
            var resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) throw new ApiException("GET " + path + " failed: HTTP " + resp.statusCode());
            return parseBody(resp.body());
        } catch (IOException | InterruptedException e) {
            throw new ApiException("GET " + path + " failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> delete(String path, String bearerToken) throws ApiException {
        try {
            var reqBuilder = HttpRequest.newBuilder(URI.create(baseUrl + path)).DELETE();
            reqBuilder.header("Authorization", "Bearer " + bearerToken);
            buildHeaders().forEach(reqBuilder::header);
            var resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) throw new ApiException("DELETE " + path + " failed: HTTP " + resp.statusCode());
            return parseBody(resp.body());
        } catch (IOException | InterruptedException e) {
            throw new ApiException("DELETE " + path + " failed", e);
        }
    }

    private HttpResponse<String> postWithHeaders(String path, Object body, Map<String, String> headers)
            throws ApiException {
        try {
            var json = mapper.writeValueAsString(body);
            var reqBuilder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            headers.forEach(reqBuilder::header);
            var resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new ApiException("POST " + path + " failed: HTTP " + resp.statusCode() + " — " + resp.body());
            }
            return resp;
        } catch (IOException | InterruptedException e) {
            throw new ApiException("POST " + path + " failed", e);
        }
    }

    private Map<String, String> buildHeaders() {
        var map = new HashMap<String, String>();
        map.put("Accept", "application/json");
        if (sessionCookie != null) map.put("Cookie", "session=" + sessionCookie);
        return map;
    }

    @SuppressWarnings("unchecked")
    private <T> T parseBody(String body) throws ApiException {
        try {
            return mapper.readValue(body, Map.class);
        } catch (Exception e) {
            throw new ApiException("Failed to parse JSON response", e);
        }
    }
}
