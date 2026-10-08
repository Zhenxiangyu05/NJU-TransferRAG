package com.yu.transferrag.service;

import java.util.*;
import static org.mockito.Mockito.*;

/** Existing business regression fixtures represent published Evidence, not readiness tests. */
public final class PublishedEvidenceFixture {
    private PublishedEvidenceFixture() {}
    public static EvidenceReadinessService gate() {
        EvidenceReadinessService gate = mock(EvidenceReadinessService.class);
        lenient().when(gate.filterCandidates(anyList())).thenAnswer(i -> i.getArgument(0));
        lenient().when(gate.readyDocumentIds(anyCollection())).thenAnswer(i -> new HashSet<>((Collection<Long>) i.getArgument(0)));
        return gate;
    }
}
