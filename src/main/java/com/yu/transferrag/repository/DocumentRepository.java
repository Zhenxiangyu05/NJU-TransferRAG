package com.yu.transferrag.repository;

import com.yu.transferrag.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select d.id as documentId, d.documentRole as documentRole,
                   t.id as taskId, t.taskKind as taskKind, t.status as status
            from Document d left join ImportTask t on t.document.id = d.id
            where d.id in :ids
            """)
    java.util.List<DocumentReadinessView> findReadinessByIds(
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from Document d where d.id = :id")
    Optional<Document> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Document> findByFileHash(String fileHash);
}
