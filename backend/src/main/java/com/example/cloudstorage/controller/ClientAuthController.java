package com.example.cloudstorage.controller;

import com.example.cloudstorage.dto.RegisterClientRequest;
import com.example.cloudstorage.dto.TokenRequest;
import com.example.cloudstorage.dto.TokenResponse;
import com.example.cloudstorage.model.ActiveToken;
import com.example.cloudstorage.model.ApiCredential;
import com.example.cloudstorage.model.ClientCredential;
import com.example.cloudstorage.service.ClientAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class ClientAuthController {

    private final ClientAuthService clientAuthService;

    public ClientAuthController(ClientAuthService clientAuthService) {
        this.clientAuthService = clientAuthService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerClient(@RequestBody RegisterClientRequest request) {
        if (request.clientId() == null || request.clientSecret() == null || request.scope() == null) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Bad Request", "message", "Missing required Client Credentials parameters"));
        }

        String appName = request.appName() != null ? request.appName() : "ExternalApp";
        ClientCredential cred = clientAuthService.registerClient(
            request.clientId(), request.clientSecret(), request.scope(), appName);

        return ResponseEntity.status(201).body(Map.of(
            "status", "SUCCESS",
            "message", "Client credentials for '" + cred.getClientId() + "' registered successfully.",
            "clientId", cred.getClientId(),
            "scope", cred.getScope()
        ));
    }

    @PostMapping("/token")
    public ResponseEntity<?> getToken(@RequestBody TokenRequest request) {
        if (request.clientId() == null || request.clientSecret() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 400,
                "error", "Bad Request",
                "message", "Missing clientId or clientSecret in request body",
                "path", "/api/v1/auth/token"
            ));
        }

        Optional<ClientCredential> clientOpt = clientAuthService.findByClientId(request.clientId());
        if (clientOpt.isPresent() && clientOpt.get().getClientSecret().equals(request.clientSecret())) {
            ClientCredential client = clientOpt.get();
            ActiveToken activeToken = clientAuthService.generateToken(client);
            String jti = "jti_" + java.util.UUID.randomUUID().toString().substring(0, 6);
            return ResponseEntity.ok(new TokenResponse(
                activeToken.getToken(), "Bearer", 3600, activeToken.getScope(), jti
            ));
        }

        Optional<ApiCredential> apiCredOpt = clientAuthService.findApiCredentialByClientId(request.clientId());
        if (apiCredOpt.isPresent() && apiCredOpt.get().getClientSecret().equals(request.clientSecret())) {
            ApiCredential apiCred = apiCredOpt.get();
            if (!apiCred.isActive()) {
                return ResponseEntity.status(403).body(Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", 403,
                    "error", "Forbidden",
                    "message", "API credentials are deactivated.",
                    "path", "/api/v1/auth/token"
                ));
            }
            ActiveToken activeToken = clientAuthService.generateTokenFromApiCredential(apiCred);
            String jti = "jti_" + java.util.UUID.randomUUID().toString().substring(0, 6);
            return ResponseEntity.ok(new TokenResponse(
                activeToken.getToken(), "Bearer", 3600, activeToken.getScope(), jti
            ));
        }

        return ResponseEntity.status(401).body(Map.of(
            "timestamp", Instant.now().toString(),
            "status", 401,
            "error", "Unauthorized",
            "message", "Bad credentials. Invalid Client ID or Client Secret.",
            "path", "/api/v1/auth/token"
        ));
    }
}
