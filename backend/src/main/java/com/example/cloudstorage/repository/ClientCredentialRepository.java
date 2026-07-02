package com.example.cloudstorage.repository;

import com.example.cloudstorage.model.ClientCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientCredentialRepository extends JpaRepository<ClientCredential, String> {
    Optional<ClientCredential> findByClientId(String clientId);
}
