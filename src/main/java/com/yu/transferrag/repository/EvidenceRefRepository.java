package com.yu.transferrag.repository;

import com.yu.transferrag.entity.EvidenceRef;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EvidenceRefRepository extends JpaRepository<EvidenceRef, Long> {

    @EntityGraph(attributePaths = {"canonicalChunk", "evidenceDocument"})
    List<EvidenceRef> findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(
            Collection<Long> canonicalChunkIds
    );
}
