package com.example.cloudstorage.service;

import com.example.cloudstorage.model.BucketFile;
import com.example.cloudstorage.repository.BucketFileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StorageService {

    private final Path storageDir;
    private final BucketFileRepository bucketFileRepository;
    private final GcsStorageService gcsStorageService;
    private final boolean gcsEnabled;

    public StorageService(@Value("${app.storage.directory:./storage}") String storageDirPath,
                          @Value("${app.storage.gcs.enabled:false}") boolean gcsEnabled,
                          BucketFileRepository bucketFileRepository,
                          Optional<GcsStorageService> gcsStorageService) {
        this.storageDir = Paths.get(storageDirPath);
        this.gcsEnabled = gcsEnabled;
        this.bucketFileRepository = bucketFileRepository;
        this.gcsStorageService = gcsStorageService.orElse(null);
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create storage directory", e);
        }
    }

    public List<BucketFile> listFiles() {
        return bucketFileRepository.findAll();
    }

    public List<BucketFile> listFilesByUserId(String userId) {
        return bucketFileRepository.findByUserId(userId);
    }

    public BucketFile uploadFile(String name, String contentType, Long size, byte[] data, String owner, String userId) {
        String id = UUID.randomUUID().toString();
        String storageUrl = null;

        if (gcsEnabled && gcsStorageService != null) {
            storageUrl = gcsStorageService.upload(id, data, contentType);
        } else {
            try {
                Files.write(storageDir.resolve(id), data);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write file to storage", e);
            }
        }

        BucketFile file = new BucketFile(id, name, contentType,
            size != null ? size : (long) data.length,
            LocalDateTime.now(), owner, storageUrl, userId);
        bucketFileRepository.save(file);

        return file;
    }

    public Optional<BucketFile> getFile(String id) {
        return bucketFileRepository.findById(id);
    }

    public byte[] readFileContent(String id) {
        if (gcsEnabled && gcsStorageService != null) {
            return gcsStorageService.download(id);
        }
        try {
            return Files.readAllBytes(storageDir.resolve(id));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file from storage", e);
        }
    }

    public boolean deleteFile(String id) {
        Optional<BucketFile> file = bucketFileRepository.findById(id);
        if (file.isEmpty()) return false;

        bucketFileRepository.deleteById(id);

        if (gcsEnabled && gcsStorageService != null) {
            gcsStorageService.delete(id);
        } else {
            try {
                Files.deleteIfExists(storageDir.resolve(id));
            } catch (IOException e) {
                // ignore file system errors for delete
            }
        }
        return true;
    }
}
