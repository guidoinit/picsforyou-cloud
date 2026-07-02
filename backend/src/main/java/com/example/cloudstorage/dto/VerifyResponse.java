package com.example.cloudstorage.dto;

public record VerifyResponse(
    String status,
    String message,
    String clientId,
    String clientSecret,
    String scope
) {}
