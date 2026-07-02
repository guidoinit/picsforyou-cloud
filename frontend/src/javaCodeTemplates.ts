export interface JavaFile {
  name: string;
  language: string;
  description: string;
  content: string;
}

export const javaCodeTemplates: Record<string, JavaFile> = {
  controller: {
    name: "CloudStorageController.java",
    language: "java",
    description: "Spring Boot REST Controller exposing bucket operations and enforcing role-based OAuth2 scopes.",
    content: `package com.example.cloudstorage.controller;

import com.example.cloudstorage.service.CloudStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/storage")
@CrossOrigin(origins = "*")
public class CloudStorageController {

    @Autowired
    private CloudStorageService storageService;

    /**
     * List all images currently stored in the cloud bucket.
     * Enforces 'SCOPE_READ' or 'SCOPE_FULL_ACCESS' authority.
     */
    @GetMapping("/files")
    @PreAuthorize("hasAuthority('SCOPE_READ') or hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<List<Map<String, Object>>> listFiles() {
        try {
            return ResponseEntity.ok(storageService.listFiles());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    /**
     * Upload a new image to the cloud bucket.
     * Enforces 'SCOPE_WRITE' or 'SCOPE_FULL_ACCESS' authority.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_WRITE') or hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Multipart file is empty"));
        }

        // Validate MIME type is an image
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("error", "Only image types are supported in this bucket"));
        }

        try {
            Map<String, Object> response = storageService.uploadImage(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to write file to GCS", "details", e.getMessage()));
        }
    }

    /**
     * Download / serve raw binary image file content directly from GCS.
     * Authenticated but does not require specific scope (public viewable resource link).
     */
    @GetMapping("/files/{id}")
    public ResponseEntity<byte[]> getFile(@PathVariable String id) {
        try {
            byte[] data = storageService.downloadFile(id);
            String contentType = storageService.getContentType(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    /**
     * Delete an image from the cloud bucket by ID.
     * Enforces 'SCOPE_WRITE' or 'SCOPE_FULL_ACCESS' authority.
     */
    @DeleteMapping("/files/{id}")
    @PreAuthorize("hasAuthority('SCOPE_WRITE') or hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<Map<String, String>> deleteFile(@PathVariable String id) {
        try {
            boolean deleted = storageService.deleteFile(id);
            if (deleted) {
                return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS", 
                    "message", "Object '" + id + "' was permanently deleted from the cloud bucket."
                ));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Object not found"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Deletion failed", "details", e.getMessage()));
        }
    }
}`
  },
  service: {
    name: "GoogleCloudStorageService.java",
    language: "java",
    description: "Service interacting with Google Cloud Storage SDK using client credentials.",
    content: `package com.example.cloudstorage.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
public class GoogleCloudStorageService implements CloudStorageService {

    @Autowired
    private Storage storage;

    @Value("\${gcp.bucket.name}")
    private String bucketName;

    @Override
    public List<Map<String, Object>> listFiles() {
        List<Map<String, Object>> fileList = new ArrayList<>();
        Iterable<Blob> blobs = storage.list(bucketName).iterateAll();
        
        for (Blob blob : blobs) {
            Map<String, Object> info = new HashMap<>();
            info.put("id", blob.getName());
            info.put("name", blob.getMetadata() != null ? 
                blob.getMetadata().get("original-name") : blob.getName());
            info.put("contentType", blob.getContentType());
            info.put("size", blob.getSize());
            info.put("uploadedAt", new Date(blob.getCreateTime()).toInstant().toString());
            info.put("owner", blob.getMetadata() != null ? 
                blob.getMetadata().get("owner") : "System-Gateway");
            info.put("publicUrl", String.format("https://storage.googleapis.com/%s/%s", bucketName, blob.getName()));
            fileList.add(info);
        }
        return fileList;
    }

    @Override
    public Map<String, Object> uploadImage(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "unnamed-image-" + System.currentTimeMillis();
        }
        
        // Formulate a secure, unique object name in GCS
        String fileExtension = originalFilename.contains(".") ? 
            originalFilename.substring(originalFilename.lastIndexOf(".")) : ".png";
        String fileId = UUID.randomUUID().toString() + fileExtension;

        // Populate object metadata on GCS
        Map<String, String> metadata = new HashMap<>();
        metadata.put("original-name", originalFilename);
        metadata.put("owner", "Java-Service-Gateway");

        BlobId blobId = BlobId.of(bucketName, fileId);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(file.getContentType())
                .setMetadata(metadata)
                .build();

        // Write binary byte stream directly to Google Cloud Storage
        Blob blob = storage.create(blobInfo, file.getBytes());

        Map<String, Object> response = new HashMap<>();
        response.put("id", fileId);
        response.put("name", originalFilename);
        response.put("contentType", file.getContentType());
        response.put("size", file.getSize());
        response.put("owner", "Java-Service-Gateway");
        response.put("uploadedAt", new Date(blob.getCreateTime()).toInstant().toString());
        response.put("publicUrl", String.format("https://storage.googleapis.com/%s/%s", bucketName, fileId));

        return response;
    }

    @Override
    public byte[] downloadFile(String id) {
        Blob blob = storage.get(BlobId.of(bucketName, id));
        if (blob == null) {
            throw new NoSuchElementException("Object '" + id + "' not found in cloud bucket");
        }
        return blob.getContent();
    }

    @Override
    public String getContentType(String id) {
        Blob blob = storage.get(BlobId.of(bucketName, id));
        return blob != null ? blob.getContentType() : "application/octet-stream";
    }

    @Override
    public boolean deleteFile(String id) {
        BlobId blobId = BlobId.of(bucketName, id);
        return storage.delete(blobId);
    }
}`
  },
  security: {
    name: "SecurityConfig.java",
    language: "java",
    description: "Configures stateless JWT OAuth2 validation and method security for Spring Boot endpoints.",
    content: `package com.example.cloudstorage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enables @PreAuthorize annotation support
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless REST APIs
            .csrf(csrf -> csrf.disable())
            
            // Set stateless session strategy
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Define access patterns
            .authorizeHttpRequests(auth -> auth
                // Allow client credentials token requests openly
                .requestMatchers("/api/v1/auth/**").permitAll()
                // All other bucket interactions require bearer token validation
                .anyRequest().authenticated()
            )
            
            // Standard JWT resource server setup
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {}) // Spring Security auto-parses client Bearer credentials
            );
            
        return http.build();
    }
}`
  },
  config: {
    name: "application.yml",
    language: "yaml",
    description: "Application environment configurations, GCS keys, and OAuth2 security providers.",
    content: `# Spring Configuration for Cloud Storage API
spring:
  application:
    name: java-cloud-storage-service
  
  # OAuth2 Resource Server Configuration (JWT validation)
  security:
    oauth2:
      resourceserver:
        jwt:
          # Point this to your identity provider's well-known configuration endpoint
          issuer-uri: https://accounts.google.com
          jwk-set-uri: https://www.googleapis.com/oauth2/v3/certs

# GCP Storage credentials and bucket details
gcp:
  bucket:
    name: my-java-service-bucket
  project:
    id: my-gcp-project-123
  credentials:
    # Location of your service-account JSON key inside your resources
    location: classpath:gcp-service-account-key.json

server:
  port: 3000
  error:
    include-message: always
    include-binding-errors: always`
  },
  pom: {
    name: "pom.xml",
    language: "xml",
    description: "Maven project descriptor with required Spring Boot, Google Cloud, and Security dependencies.",
    content: `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" 
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.3</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
    
    <groupId>com.example</groupId>
    <artifactId>cloud-storage-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>Cloud Storage Service</name>
    <description>Java Service exposing Google Cloud Storage APIs with Spring Security OAuth2</description>

    <properties>
        <java.version>17</java.version>
        <spring-cloud-gcp.version>5.0.4</spring-cloud-gcp.version>
    </properties>

    <dependencies>
        <!-- Spring Boot MVC Web Starter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Security OAuth2 Resource Server (Bearer Token Validation) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>

        <!-- Spring Boot Starter Security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- GCP Cloud Storage Spring Starter -->
        <dependency>
            <groupId>com.google.cloud</groupId>
            <artifactId>spring-cloud-gcp-starter-storage</artifactId>
        </dependency>

        <!-- Lombok for boilerplates reduction -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Testing starter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.google.cloud</groupId>
                <artifactId>spring-cloud-gcp-dependencies</artifactId>
                <version>\${spring-cloud-gcp.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>`
  }
};
