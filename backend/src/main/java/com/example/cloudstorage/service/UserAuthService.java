package com.example.cloudstorage.service;

import com.example.cloudstorage.model.ApiCredential;
import com.example.cloudstorage.model.AppUser;
import com.example.cloudstorage.model.UserSession;
import com.example.cloudstorage.repository.ApiCredentialRepository;
import com.example.cloudstorage.repository.AppUserRepository;
import com.example.cloudstorage.repository.UserSessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class UserAuthService {

    private static final int SESSION_MAX_HOURS = 24;
    private static final int VERIFICATION_TOKEN_HOURS = 48;

    private final AppUserRepository appUserRepository;
    private final UserSessionRepository userSessionRepository;
    private final ApiCredentialRepository apiCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;

    public UserAuthService(AppUserRepository appUserRepository,
                           UserSessionRepository userSessionRepository,
                           ApiCredentialRepository apiCredentialRepository,
                           PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.userSessionRepository = userSessionRepository;
        this.apiCredentialRepository = apiCredentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.secureRandom = new SecureRandom();
    }

    public boolean emailExists(String email) {
        return appUserRepository.existsByEmail(email);
    }

    public boolean usernameExists(String username) {
        return appUserRepository.existsByUsername(username);
    }

    public AppUser registerUser(String email, String password) {
        String id = "user_" + generateRandomHex(8);
        String username = email.substring(0, email.indexOf('@'));
        String baseUsername = username;
        int suffix = 1;
        while (appUserRepository.existsByUsername(username)) {
            username = baseUsername + suffix;
            suffix++;
        }
        String verificationToken = generateRandomHex(32);
        AppUser user = new AppUser(id, username, email, passwordEncoder.encode(password), LocalDateTime.now());
        user.setVerificationToken(verificationToken);
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(VERIFICATION_TOKEN_HOURS));
        return appUserRepository.save(user);
    }

    public Optional<AppUser> verifyEmail(String token) {
        Optional<AppUser> userOpt = appUserRepository.findByVerificationToken(token);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        AppUser user = userOpt.get();
        if (user.isEmailVerified()) {
            return Optional.of(user);
        }
        if (user.getVerificationTokenExpiry() != null && user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        appUserRepository.save(user);
        return Optional.of(user);
    }

    public ApiCredential createApiCredential(String userId, String username) {
        String clientId = "client_" + generateRandomHex(12);
        String clientSecret = generateRandomHex(24);
        ApiCredential cred = new ApiCredential(
            clientId, clientSecret, "FULL_ACCESS",
            username + "-api-key", userId, LocalDateTime.now()
        );
        return apiCredentialRepository.save(cred);
    }

    public Optional<ApiCredential> getApiCredentials(String userId) {
        var list = apiCredentialRepository.findByUserId(userId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<AppUser> findById(String userId) {
        return appUserRepository.findById(userId);
    }

    public Optional<AppUser> findByUsername(String username) {
        return appUserRepository.findByUsername(username);
    }

    public Optional<AppUser> findByEmail(String email) {
        return appUserRepository.findByEmail(email);
    }

    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public UserSession createSession(String userId, String username) {
        String sessionId = "sess_" + generateRandomHex(24);
        UserSession session = new UserSession(
            sessionId, userId, username,
            LocalDateTime.now(), LocalDateTime.now().plusHours(SESSION_MAX_HOURS)
        );
        return userSessionRepository.save(session);
    }

    public Optional<UserSession> findSession(String sessionId) {
        return userSessionRepository.findById(sessionId);
    }

    public void deleteSession(String sessionId) {
        userSessionRepository.deleteById(sessionId);
    }

    private String generateRandomHex(int bytes) {
        byte[] buf = new byte[bytes];
        secureRandom.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}
