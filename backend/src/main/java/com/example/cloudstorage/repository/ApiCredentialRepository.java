package com.example.cloudstorage.repository;

import com.example.cloudstorage.model.ApiCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiCredentialRepository extends JpaRepository<ApiCredential, String> {
    List<ApiCredential> findByUserId(String userId);
    Optional<ApiCredential> findByClientIdAndClientSecret(String clientId, String clientSecret);
    Optional<ApiCredential> findByClientId(String clientId);
}
