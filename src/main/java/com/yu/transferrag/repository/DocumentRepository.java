package com.yu.transferrag.repository;

import com.yu.transferrag.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByFileHash(String fileHash);
}
