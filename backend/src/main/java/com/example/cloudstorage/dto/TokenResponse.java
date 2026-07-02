package com.example.cloudstorage.dto;

public record TokenResponse(String access_token, String token_type, int expires_in, String scope, String jti) {}
