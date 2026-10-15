package com.yu.transferrag.controller;

import com.yu.transferrag.exception.AiServiceUnavailableException;
import com.yu.transferrag.exception.ImportTasksDisabledException;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, Object>> invalidBody() {
        // Default converter warnings can include untrusted JSON field names; never log the rejected body.
        return ResponseEntity.badRequest().body(Map.of("status", 400, "code", "INVALID_REQUEST", "message", "请求格式或字段不符合要求。"));
    }

    @ExceptionHandler(com.yu.transferrag.conversation.ConversationException.class)
    public ResponseEntity<Map<String, Object>> conversation(com.yu.transferrag.conversation.ConversationException e) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("status", e.status()); body.put("code", e.code()); body.put("message", e.getMessage());
        if (e.nextRequestId() != null) body.put("nextRequestId", e.nextRequestId());
        var builder = ResponseEntity.status(e.status()).cacheControl(org.springframework.http.CacheControl.noStore());
        if (e.retryAfter() != null) builder.header("Retry-After", e.retryAfter().toString());
        return builder.body(body);
    }

    @ExceptionHandler(com.yu.transferrag.protection.ProtectionException.class)
    public ResponseEntity<Map<String,Object>> protection(com.yu.transferrag.protection.ProtectionException e) {
        com.yu.transferrag.diagnostics.RagDiagnostics.recordFailure(e);
        return ResponseEntity.status(e.status()).header("Retry-After",Long.toString(Math.max(1,e.retryAfter())))
                .body(Map.of("status",e.status(),"code",e.code(),"message",e.getMessage()));
    }

    @ExceptionHandler(ImportTasksDisabledException.class)
    public ResponseEntity<Map<String, Object>> importTasksDisabled(ImportTasksDisabledException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", 503,
                "code", ImportTasksDisabledException.CODE,
                "message", ImportTasksDisabledException.USER_MESSAGE));
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> aiUnavailable(
            AiServiceUnavailableException exception, HttpServletRequest request) {
        com.yu.transferrag.diagnostics.RagDiagnostics.recordFailure(exception);
        // Never log the throwable, its message, body, headers, or user input.
        logger.warn("AI provider unavailable: stage={} status={} exception={}",
                exception.getStage(), exception.getUpstreamStatus(),
                exception.getCause().getClass().getSimpleName());
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 503);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, request.getRequestURI());
        // Reuse Spring Boot's existing error attributes, without exception/trace/message inclusion.
        Map<String, Object> body = new DefaultErrorAttributes().getErrorAttributes(
                new ServletWebRequest(request), ErrorAttributeOptions.defaults());
        body.put("code", AiServiceUnavailableException.CODE);
        body.put("message", AiServiceUnavailableException.USER_MESSAGE);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
