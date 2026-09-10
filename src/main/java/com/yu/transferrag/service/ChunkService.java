package com.yu.transferrag.service;

import com.yu.transferrag.entity.Chunk;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.repository.ChunkRepository;
import com.yu.transferrag.repository.DocumentRepository;
import com.yu.transferrag.util.StructuredPolicyChunker;
import com.yu.transferrag.util.CohortAwareTextChunker;
import com.yu.transferrag.util.TextChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkService {

    private static final Logger logger = LoggerFactory.getLogger(ChunkService.class);
    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 150;

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final DocumentParserService documentParserService;
    private final TextChunker textChunker = new TextChunker();
    private final StructuredPolicyChunker structuredPolicyChunker = new StructuredPolicyChunker();
    private final CohortAwareTextChunker cohortAwareTextChunker = new CohortAwareTextChunker();

    public ChunkService(DocumentRepository documentRepository,
                        ChunkRepository chunkRepository,
                        DocumentParserService documentParserService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.documentParserService = documentParserService;
    }

    @Transactional
    public int createChunks(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));

        String text = documentParserService.extractText(documentId);
        List<StructuredPolicyChunker.PolicyChunk> chunkData = createChunkData(document, text);

        chunkRepository.deleteByDocument_Id(documentId);

        List<Chunk> chunks = new ArrayList<>();

        for (int index = 0; index < chunkData.size(); index++) {
            StructuredPolicyChunker.PolicyChunk data = chunkData.get(index);
            Chunk chunk = new Chunk();
            chunk.setDocument(document);
            chunk.setContent(data.content());
            chunk.setChunkIndex(index);
            chunk.setPolicyYear(data.policyYear());
            chunk.setCohortYear(data.cohortYear());
            chunk.setDepartment(data.department());
            chunk.setMajor(data.major());
            chunks.add(chunk);
        }

        chunkRepository.saveAll(chunks);
        return chunks.size();
    }

    private List<StructuredPolicyChunker.PolicyChunk> createChunkData(Document document,
                                                                      String text) {
        if (isStructuredPolicyCandidate(document, text)) {
            List<StructuredPolicyChunker.PolicyChunk> structuredChunks =
                    structuredPolicyChunker.split(
                            text, CHUNK_SIZE, CHUNK_OVERLAP, document.getYear()
                    );
            if (!structuredChunks.isEmpty()) {
                logger.info(
                        "使用结构化政策切分: documentId={}, chunkCount={}",
                        document.getId(),
                        structuredChunks.size()
                );
                return structuredChunks;
            }
            logger.info("结构化政策识别失败，回退普通字符切分: documentId={}", document.getId());
        }

        if (isHistoricalCohortGuide(document)) {
            List<CohortAwareTextChunker.CohortChunk> cohortChunks =
                    cohortAwareTextChunker.split(text, CHUNK_SIZE, CHUNK_OVERLAP);
            if (cohortChunks.stream().anyMatch(chunk -> chunk.cohortYear() != null)) {
                logger.info(
                        "使用 cohort-aware 指南切分: documentId={}, chunkCount={}",
                        document.getId(), cohortChunks.size()
                );
                return cohortChunks.stream()
                        .map(chunk -> new StructuredPolicyChunker.PolicyChunk(
                                chunk.content(), null, chunk.cohortYear(), null, null
                        ))
                        .toList();
            }
        }

        return textChunker.split(text, CHUNK_SIZE, CHUNK_OVERLAP).stream()
                .map(content -> new StructuredPolicyChunker.PolicyChunk(
                        content,
                        null,
                        null,
                        null
                ))
                .toList();
    }

    private boolean isHistoricalCohortGuide(Document document) {
        String sourceType = document.getSourceType();
        if (!("PERSONAL".equalsIgnoreCase(sourceType)
                || "COMMUNITY".equalsIgnoreCase(sourceType))) {
            return false;
        }
        String title = document.getTitle();
        return title != null && (title.contains("指南")
                || title.contains("指北")
                || title.contains("经验")
                || title.contains("生存"));
    }

    private boolean isStructuredPolicyCandidate(Document document, String text) {
        boolean globalOfficial = Document.SCOPE_GLOBAL.equalsIgnoreCase(document.getScope())
                && "OFFICIAL".equalsIgnoreCase(document.getSourceType());
        if (!globalOfficial) {
            return false;
        }

        String title = document.getTitle();
        boolean policyTitle = title != null && title.contains("准入");
        boolean tableHeader = text.contains("学院名称")
                && text.contains("专业名称")
                && text.contains("准入标准");
        return policyTitle || tableHeader;
    }
}
