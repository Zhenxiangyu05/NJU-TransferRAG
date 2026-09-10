package com.yu.transferrag.dto;

public record ResolvedEntity(
        String standardName,
        String entityType,
        String department,
        String matchedText,
        EntityRole role
) {
}
