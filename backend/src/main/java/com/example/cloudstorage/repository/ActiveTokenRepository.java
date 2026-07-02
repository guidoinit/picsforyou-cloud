package com.example.cloudstorage.repository;

import com.example.cloudstorage.model.ActiveToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActiveTokenRepository extends JpaRepository<ActiveToken, String> {
    List<ActiveToken> findByExpiresAtBefore(LocalDateTime now);
}
