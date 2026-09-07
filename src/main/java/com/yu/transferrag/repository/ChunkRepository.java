package com.yu.transferrag.repository;

import com.yu.transferrag.entity.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    void deleteByDocument_Id(Long documentId);

    List<Chunk> findByDocument_IdOrderByChunkIndexAsc(Long documentId);

    @Query("""
            SELECT MAX(COALESCE(c.policyYear, c.document.year))
            FROM Chunk c
            WHERE COALESCE(c.policyYear, c.document.year) IS NOT NULL
              AND (
                    c.document.department IN :departments
                    OR (c.document.scope = :globalScope
                        AND c.document.sourceType = :officialSourceType)
                  )
            """)
    Integer findMaxEffectiveYearForDepartmentsOrGlobalOfficial(
            @Param("departments") Collection<String> departments,
            @Param("globalScope") String globalScope,
            @Param("officialSourceType") String officialSourceType
    );

    @Query("""
            SELECT MAX(COALESCE(c.policyYear, c.document.year))
            FROM Chunk c
            WHERE COALESCE(c.policyYear, c.document.year) IS NOT NULL
            """)
    Integer findMaxEffectiveYear();
}
