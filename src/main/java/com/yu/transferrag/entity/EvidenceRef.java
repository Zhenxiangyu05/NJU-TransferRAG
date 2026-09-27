package com.yu.transferrag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Provenance for one fact inside a Canonical Chunk.
 */
@Entity
@Table(
        name = "evidence_ref",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_evidence_ref_fact_source_page",
                columnNames = {"canonical_chunk_id", "fact_index", "evidence_document_id", "source_page"}
        )
)
public class EvidenceRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canonical_chunk_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Chunk canonicalChunk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_document_id", nullable = false)
    private Document evidenceDocument;

    @Column(nullable = false)
    private Integer factIndex;

    @Column(nullable = false)
    private Integer sourcePage;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String evidenceText;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Chunk getCanonicalChunk() {
        return canonicalChunk;
    }

    public void setCanonicalChunk(Chunk canonicalChunk) {
        this.canonicalChunk = canonicalChunk;
    }

    public Document getEvidenceDocument() {
        return evidenceDocument;
    }

    public void setEvidenceDocument(Document evidenceDocument) {
        this.evidenceDocument = evidenceDocument;
    }

    public Integer getFactIndex() {
        return factIndex;
    }

    public void setFactIndex(Integer factIndex) {
        this.factIndex = factIndex;
    }

    public Integer getSourcePage() {
        return sourcePage;
    }

    public void setSourcePage(Integer sourcePage) {
        this.sourcePage = sourcePage;
    }

    public String getEvidenceText() {
        return evidenceText;
    }

    public void setEvidenceText(String evidenceText) {
        this.evidenceText = evidenceText;
    }
}
