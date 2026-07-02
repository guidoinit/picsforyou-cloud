package com.example.cloudstorage.controller;

import com.example.cloudstorage.dto.DebugStateResponse;
import com.example.cloudstorage.model.ActiveToken;
import com.example.cloudstorage.model.ClientCredential;
import com.example.cloudstorage.service.ClientAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/internal")
public class DebugController {

    private final ClientAuthService clientAuthService;

    public DebugController(ClientAuthService clientAuthService) {
        this.clientAuthService = clientAuthService;
    }

    @GetMapping("/debug-state")
    public ResponseEntity<DebugStateResponse> debugState() {
        List<ClientCredential> clients = clientAuthService.findAllClients();
        List<ActiveToken> tokens = clientAuthService.findAllTokens();
        List<DebugStateResponse.ActiveTokenDebug> tokenDebug = tokens.stream()
            .map(DebugStateResponse.ActiveTokenDebug::from)
            .toList();
        return ResponseEntity.ok(new DebugStateResponse(clients, tokenDebug));
    }
}
