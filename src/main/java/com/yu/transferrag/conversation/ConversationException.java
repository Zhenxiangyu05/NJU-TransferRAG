package com.yu.transferrag.conversation;

/** All public messages are fixed; never attach a provider/Redis exception or its message. */
public final class ConversationException extends RuntimeException {
    private final int status;
    private final String code;
    private String nextRequestId;
    private boolean completionAttempted;
    private Long retryAfter;
    public ConversationException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public String nextRequestId() { return nextRequestId; }
    public ConversationException next(String value) { nextRequestId = value; return this; }
    public boolean completionAttempted() { return completionAttempted; }
    public ConversationException attempted(boolean value) { completionAttempted = value; return this; }
    public Long retryAfter() { return retryAfter; }
    public ConversationException retryAfter(long value) { retryAfter = Math.max(1, value); return this; }
    public static ConversationException unavailable() {
        return new ConversationException(503, "CONVERSATION_UNAVAILABLE", "会话服务暂不可用，请稍后重试或使用完整问题进行单轮查询。");
    }
    public static ConversationException expired() {
        return new ConversationException(410, "CONVERSATION_EXPIRED", "会话已过期或被清除，请新建对话并重新说明完整问题。");
    }
    public static ConversationException busy() {
        return new ConversationException(409, "CONVERSATION_BUSY", "本对话仍有请求正在处理，请等待完成后再提问。");
    }
    public static ConversationException clarify(String code) {
        return new ConversationException(422, code, "暂时无法可靠理解这次追问，请明确专业、年级及要询问的事项后再提问。");
    }
}
