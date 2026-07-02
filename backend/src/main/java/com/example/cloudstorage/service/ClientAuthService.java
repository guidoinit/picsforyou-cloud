package com.example.cloudstorage.service;

import com.example.cloudstorage.model.ActiveToken;
import com.example.cloudstorage.model.ApiCredential;
import com.example.cloudstorage.model.ClientCredential;
import com.example.cloudstorage.repository.ActiveTokenRepository;
import com.example.cloudstorage.repository.ApiCredentialRepository;
import com.example.cloudstorage.repository.ClientCredentialRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ClientAuthService {

    private static final int TOKEN_EXPIRY_SECONDS = 3600;

    private final ClientCredentialRepository clientCredentialRepository;
    private final ActiveTokenRepository activeTokenRepository;
    private final ApiCredentialRepository apiCredentialRepository;
    private final SecureRandom secureRandom;

    public ClientAuthService(ClientCredentialRepository clientCredentialRepository,
                             ActiveTokenRepository activeTokenRepository,
                             ApiCredentialRepository apiCredentialRepository) {
        this.clientCredentialRepository = clientCredentialRepository;
        this.activeTokenRepository = activeTokenRepository;
        this.apiCredentialRepository = apiCredentialRepository;
        this.secureRandom = new SecureRandom();
    }

    public ClientCredential registerClient(String clientId, String clientSecret, String scope, String appName) {
        ClientCredential cred = new ClientCredential(clientId, clientSecret, scope, appName);
        return clientCredentialRepository.save(cred);
    }

    public Optional<ClientCredential> findByClientId(String clientId) {
        return clientCredentialRepository.findByClientId(clientId);
    }

    public Optional<ApiCredential> findApiCredentialByClientId(String clientId) {
        return apiCredentialRepository.findByClientId(clientId);
    }

    public ActiveToken generateTokenFromApiCredential(ApiCredential cred) {
        String token = "token_" + generateRandomString(12) + generateRandomString(12);
        ActiveToken activeToken = new ActiveToken(
            token,
            cred.getScope(),
            cred.getAppName(),
            LocalDateTime.now().plusSeconds(TOKEN_EXPIRY_SECONDS),
            cred.getUserId()
        );
        return activeTokenRepository.save(activeToken);
    }

    public ActiveToken generateToken(ClientCredential client) {
        String token = "token_" + generateRandomString(12) + generateRandomString(12);
        ActiveToken activeToken = new ActiveToken(
            token,
            client.getScope(),
            client.getAppName() != null ? client.getAppName() : "ExternalApp",
            LocalDateTime.now().plusSeconds(TOKEN_EXPIRY_SECONDS),
            null
        );
        return activeTokenRepository.save(activeToken);
    }

    public List<ClientCredential> findAllClients() {
        return clientCredentialRepository.findAll();
    }

    public List<ActiveToken> findAllTokens() {
        return activeTokenRepository.findAll();
    }

    private String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
