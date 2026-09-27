package com.yu.transferrag.entity;

/**
 * Distinguishes immutable source material from reviewed knowledge cards.
 */
public enum DocumentRole {
    EVIDENCE,
    CANONICAL;

    public static DocumentRole legacyDefault(DocumentRole role) {
        return role == null ? EVIDENCE : role;
    }
}
