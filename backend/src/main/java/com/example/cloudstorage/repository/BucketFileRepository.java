package com.example.cloudstorage.repository;

import com.example.cloudstorage.model.BucketFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BucketFileRepository extends JpaRepository<BucketFile, String> {
    java.util.List<BucketFile> findByUserId(String userId);
}
