package com.yu.transferrag.diagnostics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Runs before MVC validation and Lua interceptor; never trusts a client-supplied correlation header. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RagDiagnosticsFilter extends OncePerRequestFilter {
    private static final org.springframework.web.util.pattern.PathPattern ASK =
            new org.springframework.web.util.pattern.PathPatternParser().parse("/api/rag/ask");
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return true;
        // Use MVC-compatible segment matching (context paths, encoded segments and matrix params),
        // rather than allowing a valid mapped request to evade diagnostics by spelling its URI differently.
        try {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            return !ASK.matches(org.springframework.http.server.PathContainer.parsePath(path));
        } catch (IllegalArgumentException malformedPath) {
            return true; // Leave ordinary MVC malformed-path handling unchanged.
        }
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try (var diagnostic = RagDiagnostics.begin()) {
            boolean escaped = false;
            try { chain.doFilter(request, response); }
            catch (ServletException | IOException | RuntimeException | Error error) {
                escaped = true;
                diagnostic.status(500);
                RagDiagnostics.recordFailure(error);
                throw error;
            } finally {
                if (!escaped) diagnostic.status(response.getStatus());
            }
        }
    }
}
