package com.yu.transferrag.exception;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;

import java.util.function.Supplier;

/** Only explicit provider HTTP/transport failures cross this boundary. No retries. */
public final class AiServiceUnavailableException extends RuntimeException {
    public enum Stage { EMBEDDING, ANSWERABILITY, GENERATION }

    public static final String CODE = "AI_SERVICE_UNAVAILABLE";
    public static final String USER_MESSAGE = "AI 服务暂时不可用，请稍后重试。";

    private final Stage stage;

    private AiServiceUnavailableException(Stage stage, RuntimeException cause) {
        super(USER_MESSAGE, cause);
        this.stage = stage;
    }

    public Stage getStage() { return stage; }

    public Integer getUpstreamStatus() {
        return getCause() instanceof OpenAIServiceException exception ? exception.statusCode() : null;
    }

    public static <T> T call(Stage stage, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (OpenAIServiceException | OpenAIIoException exception) {
            throw new AiServiceUnavailableException(stage, exception);
        }
    }
}
