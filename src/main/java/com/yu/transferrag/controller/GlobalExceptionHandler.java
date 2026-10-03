package com.yu.transferrag.controller;

import com.yu.transferrag.exception.AiServiceUnavailableException;
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

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> aiUnavailable(
            AiServiceUnavailableException exception, HttpServletRequest request) {
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
