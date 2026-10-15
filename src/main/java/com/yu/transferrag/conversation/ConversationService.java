package com.yu.transferrag.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yu.transferrag.dto.RagResponse;
import com.yu.transferrag.service.RagService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ConversationService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ConversationService.class);
    private final ObjectProvider<ConversationStore> stores;
    private final ConversationProperties p;
    private final StandaloneQuestionRewriter rewriter;
    private final RagService rag;
    private final com.yu.transferrag.service.EvidenceReadinessService readiness;
    private final ObjectMapper json = new ObjectMapper();
    public ConversationService(ObjectProvider<ConversationStore> stores, ConversationProperties p,
                               StandaloneQuestionRewriter rewriter, RagService rag,
                               com.yu.transferrag.service.EvidenceReadinessService readiness) {
        this.stores = stores; this.p = p; this.rewriter = rewriter; this.rag = rag; this.readiness = readiness;
    }
    private ConversationStore store() {
        if (!p.enabled()) throw new ConversationException(503, "CONVERSATION_DISABLED", "当前未启用多轮对话，请使用完整问题进行单轮查询。");
        var store = stores.getIfAvailable(); if (store == null) throw ConversationException.unavailable(); return store;
    }
    public Map<String, Object> newConversation(String previousToken) {
        ConversationStore s = store();
        if (previousToken != null) {
            try {
                var c = s.acquire(previousToken);
                try { s.delete(c); } finally { s.release(c); }
            } catch (ConversationException e) { if (e.status() != 410) throw e; }
        }
        var session = s.create();
        return Map.of("conversationToken", session.token(), "nextRequestId", session.requestId(), "expiresInSeconds", p.ttl().toSeconds());
    }
    public RagResponse ask(String token, String requestId, String question) {
        ConversationStore s = store();
        if (question == null || question.isBlank() || question.length() > p.maxQuestionChars()
                || requestId == null || !requestId.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
            throw new ConversationException(400, "CONVERSATION_INVALID_REQUEST", "本会话问题最长" + p.maxQuestionChars() + "字，请使用有效的requestId。");
        var claim = s.acquire(token); var state = claim.state();
        String hash = RedisConversationStore.hash(question);
        boolean used = false, completed = false; long start = 0; String degradation = "NONE";
        try {
            for (var receipt : state.receipts()) if (receipt.requestId().equals(requestId)) {
                if (!receipt.inputHash().equals(hash)) throw new ConversationException(409, "REQUEST_ID_CONFLICT", "同一请求标识不能用于不同问题。");
                if (requestId.equals(state.replayRequestId()) && state.replay() != null) {
                    var ids = state.replay().getSources().stream().map(com.yu.transferrag.dto.SourceResponse::getDocumentId).toList();
                    if (!readiness.readyDocumentIds(ids).containsAll(ids))
                        throw new ConversationException(409, "REQUEST_ALREADY_PROCESSED", "原回答来源的发布状态已改变，请使用新请求重新查询。");
                    RagResponse replay = json.convertValue(state.replay(), RagResponse.class);
                    replay.setNextRequestId(state.nextRequestId());
                    return replay;
                }
                throw new ConversationException(409, "REQUEST_ALREADY_PROCESSED", "该请求已处理或处理结果未知，请勿重复提交；请新建请求并明确完整问题。");
            }
            if (!requestId.equals(state.nextRequestId()))
                throw new ConversationException(409, "REQUEST_ALREADY_PROCESSED", "请求标识已失效，请使用当前会话签发的新请求标识。");
            List<ConversationStore.Receipt> receipts = new ArrayList<>(state.receipts());
            receipts.add(new ConversationStore.Receipt(requestId, hash, "PENDING"));
            if (receipts.size() > 8) receipts.removeFirst();
            // Reserve before any model call; a crash/response loss must not automatically repeat billable work.
            state = new ConversationStore.State(state.turns(), receipts, state.replayRequestId(), state.replay(), UUID.randomUUID().toString());
            s.save(claim, state);
            start = System.nanoTime();
            var resolved = rewriter.resolve(question, state.turns());
            long completionMicros = (System.nanoTime() - start) / 1000; start = 0;
            used = resolved.usedContext(); completed = resolved.completed();
            LOG.info("RAG conversation: id={} context_used={} completion_executed={} completion_us={} degradation=NONE",
                    com.yu.transferrag.diagnostics.RagDiagnostics.requestId(), used, completed, completionMicros);
            RagResponse response = used ? rag.askWithContext(question, resolved.question()) : rag.ask(question);
            if (response == null || response.getAnswer() == null || response.getAnswer().isBlank()) throw ConversationException.unavailable();
            response.setNextRequestId(state.nextRequestId());
            List<ConversationStore.Turn> turns = new ArrayList<>(used ? state.turns() : List.of());
            // A safe refusal is not a new factual topic. Failed/ambiguous requests never enter history.
            if (response.getSources() != null && !response.getSources().isEmpty()) turns.add(new ConversationStore.Turn(question, resolved.question()));
            while (turns.size() > p.maxTurns() || contextLength(turns) > p.maxContextChars()) turns.removeFirst();
            receipts.set(receipts.size() - 1, new ConversationStore.Receipt(requestId, hash, "COMPLETED"));
            RagResponse replay = json.writeValueAsBytes(response).length <= 32768 ? response : null;
            s.save(claim, new ConversationStore.State(turns, receipts, requestId, replay, state.nextRequestId()));
            return response;
        } catch (ConversationException e) {
            degradation = e.code().startsWith("CONTEXT_") ? e.code() : "SESSION_REJECTED";
            if (start != 0) LOG.info("RAG conversation: id={} context_used=false completion_executed={} completion_us={} degradation={}",
                    com.yu.transferrag.diagnostics.RagDiagnostics.requestId(), e.completionAttempted(), (System.nanoTime() - start) / 1000, degradation);
            throw e.next(state.nextRequestId());
        } catch (com.yu.transferrag.exception.AiServiceUnavailableException e) {
            com.yu.transferrag.diagnostics.RagDiagnostics.recordFailure(e);
            throw new ConversationException(503, e.CODE, e.USER_MESSAGE).next(state.nextRequestId());
        } catch (com.yu.transferrag.protection.ProtectionException e) {
            com.yu.transferrag.diagnostics.RagDiagnostics.recordFailure(e);
            if (start != 0) LOG.info("RAG conversation: id={} context_used=false completion_executed=true completion_us={} degradation=PROTECTION_REJECTED",
                    com.yu.transferrag.diagnostics.RagDiagnostics.requestId(), (System.nanoTime() - start) / 1000);
            throw new ConversationException(e.status(), e.code(), e.getMessage()).next(state.nextRequestId()).retryAfter(e.retryAfter());
        } catch (RuntimeException e) { throw ConversationException.unavailable().next(state.nextRequestId()); }
        catch (Exception e) { throw ConversationException.unavailable().next(state.nextRequestId()); }
        finally { s.release(claim); }
    }
    private static int contextLength(List<ConversationStore.Turn> turns) {
        return turns.stream().mapToInt(t -> t.question().length() + t.independentQuestion().length()).sum();
    }
}
