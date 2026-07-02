package com.example.cloudstorage.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
@ConditionalOnProperty(name = "app.storage.gcs.enabled", havingValue = "true")
public class GcsStorageService {

    private static final Logger log = LoggerFactory.getLogger(GcsStorageService.class);

    private final String bucketName;
    private final String credentialsPath;
    private Storage storage;

    public GcsStorageService(
            @Value("${app.storage.gcs.bucket}") String bucketName,
            @Value("${app.storage.gcs.credentials-path}") String credentialsPath) {
        this.bucketName = bucketName;
        this.credentialsPath = credentialsPath;
    }

    @PostConstruct
    public void init() throws IOException {
        StorageOptions options;
        if (credentialsPath != null && !credentialsPath.isBlank() && !credentialsPath.equals("/path/to/service-account-key.json")) {
            options = StorageOptions.newBuilder()
                    .setCredentials(com.google.auth.oauth2.ServiceAccountCredentials.fromStream(
                            Files.newInputStream(Paths.get(credentialsPath))))
                    .build();
            log.info("GCS initialized with service account key: {}", credentialsPath);
        } else {
            options = StorageOptions.getDefaultInstance();
            log.info("GCS initialized with application default credentials");
        }
        this.storage = options.getService();
        log.info("GCS connected to bucket: {}", bucketName);
    }

    public String upload(String blobName, byte[] data, String contentType) {
        BlobId blobId = BlobId.of(bucketName, blobName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();
        storage.create(blobInfo, data);
        String url = String.format("https://storage.googleapis.com/%s/%s", bucketName, blobName);
        log.info("Uploaded {} to GCS: {}", blobName, url);
        return url;
    }

    public byte[] download(String blobName) {
        BlobId blobId = BlobId.of(bucketName, blobName);
        Blob blob = storage.get(blobId);
        if (blob == null) {
            throw new RuntimeException("Blob not found in GCS: " + blobName);
        }
        return blob.getContent();
    }

    public void delete(String blobName) {
        BlobId blobId = BlobId.of(bucketName, blobName);
        boolean deleted = storage.delete(blobId);
        if (deleted) {
            log.info("Deleted {} from GCS", blobName);
        } else {
            log.warn("Blob not found for deletion in GCS: {}", blobName);
        }
    }
}
