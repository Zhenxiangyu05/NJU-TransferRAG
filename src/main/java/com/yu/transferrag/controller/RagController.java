package com.yu.transferrag.controller;

import com.yu.transferrag.dto.AskRequest;
import com.yu.transferrag.dto.RagResponse;
import com.yu.transferrag.service.RagService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;
    private final com.yu.transferrag.conversation.ConversationService conversations;

    public RagController(RagService ragService) {
        this(ragService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RagController(RagService ragService, com.yu.transferrag.conversation.ConversationService conversations) {
        this.ragService = ragService;
        this.conversations = conversations;
    }

    @PostMapping("/ask")
    public ResponseEntity<RagResponse> ask(@Valid @RequestBody AskRequest request) {
        String action = request.getAction() == null ? "ASK" : request.getAction();
        if ("NEW_CONVERSATION".equals(action)) {
            if (conversations == null) throw com.yu.transferrag.conversation.ConversationException.unavailable();
            var created = conversations.newConversation(request.getConversationToken());
            RagResponse response = new RagResponse();
            response.setConversationToken((String) created.get("conversationToken"));
            response.setExpiresInSeconds((Long) created.get("expiresInSeconds"));
            response.setNextRequestId((String) created.get("nextRequestId"));
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(response);
        }
        if (!"ASK".equals(action) || request.getQuestion() == null || request.getQuestion().isBlank())
            throw new com.yu.transferrag.conversation.ConversationException(400, "INVALID_QUESTION", "请输入有效的问题。");
        if (request.getConversationToken() != null) {
            if (conversations == null) throw com.yu.transferrag.conversation.ConversationException.unavailable();
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(conversations.ask(request.getConversationToken(), request.getRequestId(), request.getQuestion()));
        }
        if (request.getRequestId() != null)
            throw new com.yu.transferrag.conversation.ConversationException(400, "CONVERSATION_REQUIRED", "requestId必须与会话凭据一同提交。");
        return ResponseEntity.ok(ragService.ask(request.getQuestion()));
    }
}
