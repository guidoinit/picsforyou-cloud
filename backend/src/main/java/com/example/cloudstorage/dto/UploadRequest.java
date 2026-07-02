package com.example.cloudstorage.dto;

public record UploadRequest(String name, String type, Long size, String data) {}
