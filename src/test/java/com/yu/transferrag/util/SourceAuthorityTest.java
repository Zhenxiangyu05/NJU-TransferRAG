package com.yu.transferrag.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceAuthorityTest {

    @Test
    void shouldRecognizeAllSupportedOfficialSourceTypes() {
        assertTrue(SourceAuthority.isOfficialSource("OFFICIAL"));
        assertTrue(SourceAuthority.isOfficialSource("OFFICIAL_PDF"));
        assertTrue(SourceAuthority.isOfficialSource(" official_pdf "));
    }

    @Test
    void shouldTreatReferenceAndUnknownSourceTypesAsNonOfficial() {
        assertFalse(SourceAuthority.isOfficialSource("GITHUB"));
        assertFalse(SourceAuthority.isOfficialSource("PERSONAL"));
        assertFalse(SourceAuthority.isOfficialSource("COMMUNITY"));
        assertFalse(SourceAuthority.isOfficialSource(null));
        assertFalse(SourceAuthority.isOfficialSource(" "));
    }
}
