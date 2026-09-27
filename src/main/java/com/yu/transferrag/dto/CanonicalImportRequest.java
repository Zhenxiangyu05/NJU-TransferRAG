package com.yu.transferrag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * A reviewed knowledge card. Canonical content is always linked to source
 * evidence at fact level and is never treated as an original source file.
 */
public record CanonicalImportRequest(
        @NotBlank String title,
        @NotBlank String department,
        @NotNull @Min(1900) @Max(2200) Integer year,
        String scope,
        @NotEmpty List<@Valid CanonicalSectionRequest> sections
) {

    public record CanonicalSectionRequest(
            @NotBlank String section,
            Integer policyYear,
            Integer cohortYear,
            String department,
            String major,
            @NotEmpty List<@Valid CanonicalFactRequest> facts
    ) {
    }

    public record CanonicalFactRequest(
            @NotBlank String text,
            @NotEmpty List<@Valid EvidenceRefRequest> evidenceRefs
    ) {
    }

    public record EvidenceRefRequest(
            @NotNull @Positive Long sourceDocumentId,
            @NotNull @Positive Integer page,
            @NotBlank String evidenceText
    ) {
    }
}
