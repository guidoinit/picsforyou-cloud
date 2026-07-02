package com.example.cloudstorage.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyRequest(
    @NotBlank String token
) {}
