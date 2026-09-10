package com.yu.transferrag.service;

import com.yu.transferrag.dto.MetadataConsistencyReport;
import com.yu.transferrag.entity.Chunk;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.repository.ChunkRepository;
import com.yu.transferrag.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetadataConsistencyServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ChunkRepository chunkRepository;

    @Mock
    private QdrantMetadataReader qdrantMetadataReader;

    private MetadataConsistencyService service;

    @BeforeEach
    void setUp() {
        service = new MetadataConsistencyService(
                documentRepository,
                chunkRepository,
                qdrantMetadataReader
        );
    }

    @Test
    void shouldReturnOkWhenMysqlAndQdrantMetadataMatch() {
        Document document = document(14L, "电子科学与工程学院", 2026, "DEPARTMENT", "PERSONAL");
        Chunk chunk = chunk(89L, 0, document, "电子科学与工程学院", "电子信息类", null);
        stub(document, chunk, point("point-1", metadata(
                "14", "89", "电子科学与工程学院", "电子科学与工程学院",
                "电子信息类", 2026, null, 2026, "DEPARTMENT", "PERSONAL"
        )));

        MetadataConsistencyReport report = service.check(14L);

        assertEquals("OK", report.status());
        assertTrue(report.issues().isEmpty());
        assertTrue(report.warnings().isEmpty());
        assertEquals(1, report.mysqlChunkCount());
        assertEquals(1, report.qdrantPointCount());
    }

    @Test
    void shouldReportMismatchesMissingFieldsAndMysqlWarnings() {
        Document document = document(13L, "现代工程与应用科学学院", 2026,
                "DEPARTMENT", "COMMUNITY");
        Chunk chunk = chunk(87L, 0, document, null, null, null);
        Map<String, Object> metadata = metadata(
                "13", "87", "现代工程学院", null,
                null, 2026, null, null, null, "COMMUNITY"
        );
        stub(document, chunk, point("point-2", metadata));

        MetadataConsistencyReport report = service.check(13L);

        assertEquals("MISMATCH", report.status());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.contains("department")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.contains("effectiveYear")));
        assertTrue(report.issues().stream().anyMatch(issue -> issue.contains("scope")));
        assertTrue(report.warnings().stream().anyMatch(warning -> warning.contains("chunk.department")));
        assertTrue(report.warnings().stream().anyMatch(warning -> warning.contains("chunk.major")));
    }

    @Test
    void shouldCheckCohortYearBetweenMysqlAndQdrant() {
        Document document = document(6L, "本科生院", 2026, "GLOBAL", "OFFICIAL");
        Chunk chunk = chunk(160L, 0, document, "文学院", "汉语言文学", 2026);
        chunk.setCohortYear(2025);
        stub(document, chunk, point("point-3", metadata(
                "6", "160", "本科生院", "文学院", "汉语言文学",
                2026, 2026, 2024, 2026, "GLOBAL", "OFFICIAL"
        )));

        MetadataConsistencyReport report = service.check(6L);

        assertEquals("MISMATCH", report.status());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.contains("cohortYear")));
    }

    private void stub(Document document,
                      Chunk chunk,
                      QdrantMetadataReader.QdrantMetadataPoint point) {
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(chunkRepository.findByDocument_IdOrderByChunkIndexAsc(document.getId()))
                .thenReturn(List.of(chunk));
        when(qdrantMetadataReader.findByDocumentId(document.getId())).thenReturn(List.of(point));
    }

    private Document document(Long id,
                              String department,
                              Integer year,
                              String scope,
                              String sourceType) {
        Document document = new Document();
        document.setId(id);
        document.setTitle("test");
        document.setDepartment(department);
        document.setYear(year);
        document.setScope(scope);
        document.setSourceType(sourceType);
        return document;
    }

    private Chunk chunk(Long id,
                        int index,
                        Document document,
                        String department,
                        String major,
                        Integer policyYear) {
        Chunk chunk = new Chunk();
        chunk.setId(id);
        chunk.setChunkIndex(index);
        chunk.setContent("content");
        chunk.setDocument(document);
        chunk.setDepartment(department);
        chunk.setMajor(major);
        chunk.setPolicyYear(policyYear);
        return chunk;
    }

    private QdrantMetadataReader.QdrantMetadataPoint point(String id, Map<String, Object> metadata) {
        return new QdrantMetadataReader.QdrantMetadataPoint(id, metadata);
    }

    private Map<String, Object> metadata(String documentId,
                                         String chunkId,
                                         String department,
                                         String chunkDepartment,
                                         String major,
                                         Integer year,
                                         Integer policyYear,
                                         Integer effectiveYear,
                                         String scope,
                                         String sourceType) {
        return metadata(documentId, chunkId, department, chunkDepartment, major, year,
                policyYear, null, effectiveYear, scope, sourceType);
    }

    private Map<String, Object> metadata(String documentId,
                                         String chunkId,
                                         String department,
                                         String chunkDepartment,
                                         String major,
                                         Integer year,
                                         Integer policyYear,
                                         Integer cohortYear,
                                         Integer effectiveYear,
                                         String scope,
                                         String sourceType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "documentId", documentId);
        put(metadata, "chunkId", chunkId);
        put(metadata, "department", department);
        put(metadata, "chunkDepartment", chunkDepartment);
        put(metadata, "major", major);
        put(metadata, "year", year);
        put(metadata, "policyYear", policyYear);
        put(metadata, "cohortYear", cohortYear);
        put(metadata, "effectiveYear", effectiveYear);
        put(metadata, "scope", scope);
        put(metadata, "sourceType", sourceType);
        return Map.copyOf(metadata);
    }

    private void put(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
