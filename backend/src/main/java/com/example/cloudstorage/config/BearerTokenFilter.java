package com.example.cloudstorage.config;

import com.example.cloudstorage.model.ActiveToken;
import com.example.cloudstorage.model.ApiCredential;
import com.example.cloudstorage.repository.ActiveTokenRepository;
import com.example.cloudstorage.repository.ApiCredentialRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Order(1)
public class BearerTokenFilter extends OncePerRequestFilter {

    private final ActiveTokenRepository activeTokenRepository;
    private final ApiCredentialRepository apiCredentialRepository;
    private final ObjectMapper objectMapper;

    public BearerTokenFilter(ActiveTokenRepository activeTokenRepository,
                             ApiCredentialRepository apiCredentialRepository,
                             ObjectMapper objectMapper) {
        this.activeTokenRepository = activeTokenRepository;
        this.apiCredentialRepository = apiCredentialRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (!path.startsWith("/api/v1/storage/")) {
            chain.doFilter(request, response);
            return;
        }

        // Allow public read access to individual file downloads (for <img> tags)
        if (request.getMethod().equals("GET") && path.matches("/api/v1/storage/files/[^/]+")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            sendError(response, 401, "Unauthorized",
                "Authentication is required (Bearer <token> or Basic <base64(clientId:clientSecret)>)", request);
            return;
        }

        String scope;
        String appName;

        if (authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Optional<ActiveToken> tokenOpt = activeTokenRepository.findById(token);

            if (tokenOpt.isEmpty()) {
                sendError(response, 401, "Unauthorized",
                    "Invalid or expired access token", request);
                return;
            }

            ActiveToken activeToken = tokenOpt.get();
            if (activeToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                activeTokenRepository.delete(activeToken);
                sendError(response, 401, "Unauthorized",
                    "Access token has expired", request);
                return;
            }

            scope = activeToken.getScope();
            appName = activeToken.getAppName();

            request.setAttribute("activeToken", activeToken);
            if (activeToken.getUserId() != null) {
                request.setAttribute("userId", activeToken.getUserId());
            }

        } else if (authHeader.startsWith("Basic ")) {
            String base64 = authHeader.substring(6);
            String decoded;
            try {
                decoded = new String(Base64.getDecoder().decode(base64));
            } catch (Exception e) {
                sendError(response, 401, "Unauthorized",
                    "Invalid Basic authentication encoding", request);
                return;
            }

            int colonIdx = decoded.indexOf(':');
            if (colonIdx < 0) {
                sendError(response, 401, "Unauthorized",
                    "Basic auth must be formatted as clientId:clientSecret", request);
                return;
            }

            String clientId = decoded.substring(0, colonIdx);
            String clientSecret = decoded.substring(colonIdx + 1);

            Optional<ApiCredential> credOpt = apiCredentialRepository.findByClientIdAndClientSecret(clientId, clientSecret);
            if (credOpt.isEmpty()) {
                sendError(response, 401, "Unauthorized",
                    "Invalid client credentials", request);
                return;
            }

            ApiCredential cred = credOpt.get();
            if (!cred.isActive()) {
                sendError(response, 403, "Forbidden",
                    "API credentials are deactivated", request);
                return;
            }

            scope = cred.getScope();
            appName = cred.getAppName();

            ActiveToken synthetic = new ActiveToken("basic-auth", scope, appName, LocalDateTime.now().plusDays(1), cred.getUserId());
            request.setAttribute("activeToken", synthetic);
            request.setAttribute("userId", cred.getUserId());

        } else {
            sendError(response, 401, "Unauthorized",
                "Unsupported authorization scheme. Use Bearer <token> or Basic <base64(clientId:clientSecret)>", request);
            return;
        }

        String method = request.getMethod();
        boolean authorized = checkScope(scope, method, path);
        if (!authorized) {
            String required = determineRequiredScope(method, path);
            sendError(response, 403, "Forbidden",
                "Access Denied: Insufficient scope. Required: " + required + ", Actual: " + scope, request);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean checkScope(String scope, String method, String path) {
        if ("FULL_ACCESS".equals(scope)) return true;

        if ("GET".equals(method) && path.equals("/api/v1/storage/files")) {
            return "READ".equals(scope) || "READ_WRITE".equals(scope);
        }
        if ("POST".equals(method) && path.equals("/api/v1/storage/upload")) {
            return "READ_WRITE".equals(scope);
        }
        if ("DELETE".equals(method) && path.startsWith("/api/v1/storage/files/")
            && path.length() > "/api/v1/storage/files/".length()) {
            return "READ_WRITE".equals(scope);
        }
        if ("GET".equals(method) && path.startsWith("/api/v1/storage/files/")
            && path.length() > "/api/v1/storage/files/".length()) {
            return true;
        }
        return false;
    }

    private String determineRequiredScope(String method, String path) {
        if ("GET".equals(method) && path.equals("/api/v1/storage/files")) return "READ";
        if ("POST".equals(method) && path.equals("/api/v1/storage/upload")) return "WRITE";
        if ("DELETE".equals(method)) return "WRITE";
        return "UNKNOWN";
    }

    private void sendError(HttpServletResponse response, int status, String error,
                           String message, HttpServletRequest request) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
