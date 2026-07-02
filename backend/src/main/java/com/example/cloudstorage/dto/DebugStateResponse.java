package com.example.cloudstorage.dto;

import com.example.cloudstorage.model.ActiveToken;
import com.example.cloudstorage.model.ClientCredential;

import java.util.List;

public record DebugStateResponse(List<ClientCredential> clients, List<ActiveTokenDebug> activeTokens) {

    public record ActiveTokenDebug(String token, String scope, String appName, String expiresAt) {
        public static ActiveTokenDebug from(ActiveToken at) {
            return new ActiveTokenDebug(at.getToken(), at.getScope(), at.getAppName(), at.getExpiresAt().toString());
        }
    }
}
