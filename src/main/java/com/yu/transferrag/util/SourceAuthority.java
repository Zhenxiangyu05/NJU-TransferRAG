package com.yu.transferrag.util;

import java.util.Locale;
import java.util.Set;

public final class SourceAuthority {

    private static final Set<String> OFFICIAL_SOURCE_TYPES = Set.of(
            "OFFICIAL",
            "OFFICIAL_PDF"
    );

    private SourceAuthority() {
    }

    public static boolean isOfficialSource(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return false;
        }
        return OFFICIAL_SOURCE_TYPES.contains(
                sourceType.trim().toUpperCase(Locale.ROOT)
        );
    }
}
