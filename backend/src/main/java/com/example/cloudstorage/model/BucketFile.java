package com.example.cloudstorage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "bucket_files")
public class BucketFile {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String contentType;

    private Long size;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Column(nullable = false)
    private String owner;

    @Column(length = 1024)
    private String storageUrl;

    @Column
    private String userId;

    public BucketFile() {}

    public BucketFile(String id, String name, String contentType, Long size, LocalDateTime uploadedAt, String owner, String storageUrl, String userId) {
        this.id = id;
        this.name = name;
        this.contentType = contentType;
        this.size = size;
        this.uploadedAt = uploadedAt;
        this.owner = owner;
        this.storageUrl = storageUrl;
        this.userId = userId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getStorageUrl() { return storageUrl; }
    public void setStorageUrl(String storageUrl) { this.storageUrl = storageUrl; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
