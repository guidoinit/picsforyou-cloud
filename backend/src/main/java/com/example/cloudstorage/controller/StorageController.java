package com.example.cloudstorage.controller;

import com.example.cloudstorage.dto.FileResponse;
import com.example.cloudstorage.dto.UploadRequest;
import com.example.cloudstorage.model.ActiveToken;
import com.example.cloudstorage.model.AppUser;
import com.example.cloudstorage.model.BucketFile;
import com.example.cloudstorage.model.SubscriptionPlan;
import com.example.cloudstorage.repository.AppUserRepository;
import com.example.cloudstorage.repository.SubscriptionPlanRepository;
import com.example.cloudstorage.service.StorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/storage")
public class StorageController {

    private final StorageService storageService;
    private final AppUserRepository appUserRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public StorageController(StorageService storageService, AppUserRepository appUserRepository,
                             SubscriptionPlanRepository subscriptionPlanRepository) {
        this.storageService = storageService;
        this.appUserRepository = appUserRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    @GetMapping("/files")
    public ResponseEntity<List<FileResponse>> listFiles(
            @RequestAttribute(value = "userId", required = false) String userId) {
        List<BucketFile> files = (userId != null)
            ? storageService.listFilesByUserId(userId)
            : List.of();
        List<FileResponse> response = files.stream()
            .map(f -> new FileResponse(
                f.getId(), f.getName(), f.getContentType(), f.getSize(),
                f.getUploadedAt().toString(), f.getOwner(),
                f.getStorageUrl() != null ? f.getStorageUrl() : "/api/v1/storage/files/" + f.getId()))
            .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestBody UploadRequest request,
                                        @RequestAttribute("activeToken") ActiveToken token,
                                        @RequestAttribute(value = "userId", required = false) String userId) {
        if (request.name() == null || request.type() == null || request.data() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 400,
                "error", "Bad Request",
                "message", "Payload must contain name, type, and base64 data attributes",
                "path", "/api/v1/storage/upload"
            ));
        }

        if (!request.type().startsWith("image/")) {
            return ResponseEntity.status(415).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 415,
                "error", "Unsupported Media Type",
                "message", "Only image uploads are allowed in this cloud bucket configuration",
                "path", "/api/v1/storage/upload"
            ));
        }

        byte[] decodedData;
        try {
            decodedData = Base64.getDecoder().decode(request.data());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request",
                "message", "Invalid base64 data"
            ));
        }

        if (userId != null) {
            Optional<AppUser> userOpt = appUserRepository.findById(userId);
            if (userOpt.isPresent()) {
                AppUser user = userOpt.get();
                Optional<SubscriptionPlan> planOpt = subscriptionPlanRepository.findById(user.getSubscriptionPlanId());
                if (planOpt.isPresent()) {
                    SubscriptionPlan plan = planOpt.get();
                    long fileSizeKb = (request.size() != null ? request.size() : decodedData.length) / 1024;
                    if (plan.getStorageLimitMb() >= 0 && user.getStorageUsedKb() + fileSizeKb > plan.getStorageLimitMb() * 1024) {
                        return ResponseEntity.status(403).body(Map.of(
                            "timestamp", Instant.now().toString(),
                            "status", 403,
                            "error", "Forbidden",
                            "message", "Storage limit exceeded. Your " + plan.getName() + " plan allows up to " + plan.getStorageLimitMb() + " MB. Upgrade your plan to upload more files.",
                            "path", "/api/v1/storage/upload"
                        ));
                    }
                }
            }
        }

        BucketFile file = storageService.uploadFile(
            request.name(), request.type(), request.size(), decodedData, token.getAppName(), userId);

        if (userId != null && file.getSize() != null) {
            appUserRepository.findById(userId).ifPresent(user -> {
                user.addStorageKb(file.getSize() / 1024);
                appUserRepository.save(user);
            });
        }

        return ResponseEntity.status(201).body(Map.of(
            "status", "CREATED",
            "message", "File successfully uploaded to Google Cloud Storage bucket 'picsforall'",
            "id", file.getId(),
            "name", file.getName(),
            "contentType", file.getContentType(),
            "size", file.getSize(),
            "owner", file.getOwner(),
            "uploadedAt", file.getUploadedAt().toString(),
            "publicUrl", file.getStorageUrl() != null ? file.getStorageUrl() : "/api/v1/storage/files/" + file.getId(),
            "storageUsedKb", userId != null ? appUserRepository.findById(userId).map(AppUser::getStorageUsedKb).orElse(0L) : 0
        ));
    }

    @GetMapping("/files/{id}")
    public ResponseEntity<?> downloadFile(@PathVariable String id) {
        Optional<BucketFile> fileOpt = storageService.getFile(id);
        if (fileOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 404,
                "error", "Not Found",
                "message", "Object with ID '" + id + "' does not exist in bucket 'picsforall'",
                "path", "/api/v1/storage/files/" + id
            ));
        }

        BucketFile file = fileOpt.get();
        try {
            byte[] content = storageService.readFileContent(id);
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.length))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(content);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Internal Server Error",
                "message", "Failed to decode binary data stream from storage backend"
            ));
        }
    }

    @DeleteMapping("/files/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable String id) {
        Optional<BucketFile> fileOpt = storageService.getFile(id);
        if (fileOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 404,
                "error", "Not Found",
                "message", "Object with ID '" + id + "' does not exist in bucket 'picsforall'",
                "path", "/api/v1/storage/files/" + id
            ));
        }

        BucketFile file = fileOpt.get();
        String userId = file.getUserId();

        if (userId != null && file.getSize() != null) {
            appUserRepository.findById(userId).ifPresent(user -> {
                user.addStorageKb(-(file.getSize() / 1024));
                appUserRepository.save(user);
            });
        }

        storageService.deleteFile(id);

        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "message", "Object '" + id + "' has been permanently deleted from bucket 'picsforall'."
        ));
    }
}
