package com.example.cloudstorage.dto;

public record FileResponse(String id, String name, String contentType, Long size, String uploadedAt, String owner, String publicUrl) {}
