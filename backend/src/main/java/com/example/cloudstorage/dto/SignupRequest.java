package com.example.cloudstorage.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @Email(message = "Valid email is required") String email,
    @Size(min = 4, message = "Password must be at least 4 characters") String password
) {}
