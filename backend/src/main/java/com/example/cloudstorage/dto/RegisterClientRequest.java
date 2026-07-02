package com.example.cloudstorage.dto;

public record RegisterClientRequest(String clientId, String clientSecret, String scope, String appName) {}
