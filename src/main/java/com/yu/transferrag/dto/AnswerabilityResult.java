package com.yu.transferrag.dto;

import java.util.List;

public record AnswerabilityResult(
        boolean answerable,
        List<String> evidenceCitationIds,
        String reason
) {

    public AnswerabilityResult {
        evidenceCitationIds = evidenceCitationIds == null
                ? List.of()
                : List.copyOf(evidenceCitationIds);
    }

    public static AnswerabilityResult notAnswerable(String reason) {
        return new AnswerabilityResult(false, List.of(), reason);
    }
}
