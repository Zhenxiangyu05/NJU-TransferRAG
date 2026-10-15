package com.yu.transferrag.conversation;

import com.yu.transferrag.dto.RagResponse;
import java.util.List;

public interface ConversationStore {
    record Turn(String question, String independentQuestion) { }
    record Receipt(String requestId, String inputHash, String status) { }
    record State(List<Turn> turns, List<Receipt> receipts, String replayRequestId, RagResponse replay, String nextRequestId) {
        public State { turns = List.copyOf(turns); receipts = List.copyOf(receipts); }
        public static State empty() { return new State(List.of(), List.of(), null, null, java.util.UUID.randomUUID().toString()); }
    }
    record Session(String token, String requestId) { }
    record Claim(String key, String owner, State state) { }
    Session create();
    Claim acquire(String token);
    void save(Claim claim, State state);
    void release(Claim claim);
    void delete(Claim claim);
}
