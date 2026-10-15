package com.yu.transferrag.diagnostics;

import com.openai.errors.OpenAIServiceException;
import com.yu.transferrag.protection.ProtectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.EOFException;
import java.io.InterruptedIOException;
import java.net.*;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.*;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;

/** Synchronous public RAG diagnostics only: no payloads, exception messages, stacks or transport hooks. */
public final class RagDiagnostics {
    private static final Logger LOG = LoggerFactory.getLogger(RagDiagnostics.class);
    private static final ThreadLocal<Request> CURRENT = new ThreadLocal<>();
    public static final String MDC_KEY = "ragRequestId";
    public enum Stage { CONTEXT_COMPLETION, QUERY_EMBEDDING, RETRIEVAL, ANSWERABILITY, GENERATION, REQUEST }
    public enum Category {
        NONE, CONNECT_TIMEOUT, READ_OR_REQUEST_TIMEOUT, TIMEOUT_UNSPECIFIED, DNS_FAILURE, TLS_FAILURE,
        CONNECTION_RESET, EOF, CONNECTION_FAILURE, SOCKET_IO, INTERRUPTED_IO,
        HTTP_4XX, HTTP_5XX, CIRCUIT_OPEN, BULKHEAD_REJECTED, PROTECTION_REJECTED, OTHER
    }
    public record Failure(String outer, String root, Integer httpStatus, Category category) {}
    private record Frame(Stage stage, long start, long[] children) {}

    private RagDiagnostics() {}

    public static Request begin() { return new Request(); }
    public static String requestId() { return CURRENT.get() == null ? null : CURRENT.get().id; }

    /** Without a public-request scope this is a no-op; in particular import workers remain untouched. */
    public static <T> T measure(Stage stage, Supplier<T> operation) {
        Request request = CURRENT.get();
        if (request == null) return operation.get();
        Frame frame = new Frame(stage, System.nanoTime(), new long[1]);
        request.frames.push(frame);
        Throwable failure = null;
        try { return operation.get(); }
        catch (RuntimeException | Error error) {
            failure = error;
            request.failure(stage, error);
            throw error;
        } finally {
            long elapsed = System.nanoTime() - frame.start();
            request.frames.pop();
            if (!request.frames.isEmpty()) request.frames.peek().children()[0] += elapsed;
            long exclusive = Math.max(0, elapsed - frame.children()[0]);
            request.nanos.merge(stage, exclusive, Long::sum);
            request.counts.merge(stage, 1, Integer::sum);
            Failure safe = failure == null ? new Failure("NONE", "NONE", null, Category.NONE) : classify(failure);
            LOG.info("RAG diagnostic stage: id={} stage={} elapsed_us={} exclusive_us={} outcome={} exception={} root={} http_status={} category={} http_attempts=NOT_OBSERVABLE",
                    request.id, stage, elapsed / 1000, exclusive / 1000, failure == null ? "SUCCESS" : "ERROR",
                    safe.outer(), safe.root(), safe.httpStatus(), safe.category());
        }
    }

    public static void recordFailure(Throwable error) {
        Request request = CURRENT.get();
        if (request != null) {
            request.failure(Stage.REQUEST, error);
            // Advice/filter knows the outermost application/servlet wrapper. Preserve the
            // first (innermost) failed stage but update the safe exception classification.
            request.failure = classify(error);
        }
    }

    /** Class names are allowlisted; user-defined exception names cannot inject arbitrary log data. */
    private static String safeClass(Throwable error) {
        String name = error.getClass().getName();
        return switch (name) {
            case "com.yu.transferrag.exception.AiServiceUnavailableException",
                 "com.yu.transferrag.protection.ProtectionException",
                 "com.openai.errors.OpenAIIoException", "com.openai.errors.OpenAIServiceException",
                 "com.openai.errors.InternalServerException", "com.openai.errors.RateLimitException",
                 "com.openai.errors.NotFoundException", "com.openai.errors.BadRequestException",
                 "com.openai.errors.UnauthorizedException", "com.openai.errors.PermissionDeniedException",
                 "com.openai.errors.UnprocessableEntityException", "com.openai.errors.UnexpectedStatusCodeException",
                 "com.openai.errors.OpenAIRetryableException", "com.openai.errors.OpenAIInvalidDataException",
                 "java.io.IOException", "java.io.InterruptedIOException", "java.io.EOFException",
                 "java.net.SocketTimeoutException", "java.net.ConnectException", "java.net.SocketException",
                 "java.net.UnknownHostException", "java.net.http.HttpConnectTimeoutException",
                 "java.net.http.HttpTimeoutException", "javax.net.ssl.SSLException",
                 "javax.net.ssl.SSLHandshakeException", "javax.net.ssl.SSLProtocolException",
                 "java.security.cert.CertificateException", "java.security.cert.CertPathValidatorException",
                 "java.lang.IllegalStateException", "java.lang.IllegalArgumentException",
                 "java.lang.RuntimeException", "java.util.concurrent.ExecutionException",
                 "java.util.concurrent.CompletionException", "jakarta.servlet.ServletException",
                 "io.github.resilience4j.circuitbreaker.CallNotPermittedException",
                 "io.github.resilience4j.bulkhead.BulkheadFullException" -> name;
            default -> "OTHER";
        };
    }

    public static Failure classify(Throwable error) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cursor = Objects.requireNonNull(error);
        while (cursor != null && chain.size() < 32 && seen.add(cursor)) {
            chain.add(cursor);
            cursor = cursor.getCause();
        }
        Integer status = null;
        Category category = Category.OTHER;
        for (Throwable node : chain) {
            if (node instanceof ProtectionException protection) {
                category = switch (protection.code()) {
                    case "AI_CIRCUIT_OPEN" -> Category.CIRCUIT_OPEN;
                    case "AI_BUSY" -> Category.BULKHEAD_REJECTED;
                    default -> Category.PROTECTION_REJECTED;
                };
                status = validStatus(protection.status());
                break;
            }
            if (node instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) { category = Category.CIRCUIT_OPEN; break; }
            if (node instanceof io.github.resilience4j.bulkhead.BulkheadFullException) { category = Category.BULKHEAD_REJECTED; break; }
            if (node instanceof OpenAIServiceException service) {
                status = validStatus(service.statusCode());
                category = status != null && status >= 500 ? Category.HTTP_5XX : status != null && status >= 400 ? Category.HTTP_4XX : Category.OTHER;
                break;
            }
            if (node instanceof HttpConnectTimeoutException) { category = Category.CONNECT_TIMEOUT; break; }
            if (node instanceof SocketTimeoutException socket) {
                // SocketTimeoutException alone does not identify the phase. Inspect only known call sites;
                // never log frames or inspect an arbitrary exception message to infer a timeout.
                boolean connect = Arrays.stream(socket.getStackTrace()).anyMatch(frame ->
                        (frame.getClassName().equals("sun.nio.ch.NioSocketImpl") && frame.getMethodName().equals("timedFinishConnect"))
                        || (frame.getClassName().equals("okhttp3.internal.connection.RealConnection") && frame.getMethodName().equals("connectSocket")));
                boolean read = Arrays.stream(socket.getStackTrace()).anyMatch(frame ->
                        (frame.getClassName().equals("sun.nio.ch.NioSocketImpl") && frame.getMethodName().equals("timedRead"))
                        || (frame.getClassName().startsWith("okio.AsyncTimeout$") && frame.getMethodName().equals("read"))
                        || (frame.getClassName().equals("okhttp3.internal.http2.Http2Stream") && frame.getMethodName().equals("takeHeaders")));
                category = connect ? Category.CONNECT_TIMEOUT : read ? Category.READ_OR_REQUEST_TIMEOUT : Category.TIMEOUT_UNSPECIFIED;
                break;
            }
            if (node instanceof HttpTimeoutException) { category = Category.READ_OR_REQUEST_TIMEOUT; break; }
            if (node instanceof UnknownHostException) { category = Category.DNS_FAILURE; break; }
            if (node instanceof SSLException) { category = Category.TLS_FAILURE; break; }
            if (node instanceof EOFException) { category = Category.EOF; break; }
            if (node instanceof ConnectException) { category = Category.CONNECTION_FAILURE; break; }
            if (node instanceof SocketException socket) {
                // Only exact fixed OS tokens are mapped; the message is NEVER returned or logged.
                if (Set.of("Connection reset", "Connection reset by peer").contains(Objects.toString(socket.getMessage(), ""))) category = Category.CONNECTION_RESET;
                else if (category == Category.OTHER) category = Category.SOCKET_IO;
            } else if (node instanceof InterruptedIOException) category = Category.INTERRUPTED_IO;
        }
        return new Failure(safeClass(error), cursor == null ? safeClass(chain.getLast()) : "NOT_OBSERVABLE", status, category);
    }

    private static Integer validStatus(int status) { return status >= 100 && status <= 599 ? status : null; }

    public static final class Request implements AutoCloseable {
        private final String id = UUID.randomUUID().toString().replace("-", "");
        private final long start = System.nanoTime();
        private final Request previous = CURRENT.get();
        private final String previousMdc = MDC.get(MDC_KEY);
        private final Deque<Frame> frames = new ArrayDeque<>();
        private final EnumMap<Stage, Long> nanos = new EnumMap<>(Stage.class);
        private final EnumMap<Stage, Integer> counts = new EnumMap<>(Stage.class);
        private Stage failedStage;
        private Failure failure;
        private int status = 200;
        private boolean closed;
        private Request() { CURRENT.set(this); MDC.put(MDC_KEY, id); }
        public void status(int value) { status = value >= 100 && value <= 599 ? value : 500; }
        private void failure(Stage stage, Throwable error) {
            if (failure == null) { failedStage = stage; failure = classify(error); }
        }
        private long micros(Stage stage) { return nanos.getOrDefault(stage, 0L) / 1000; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                Failure safe = failure == null ? new Failure("NONE", "NONE", null, Category.NONE) : failure;
                LOG.info("RAG diagnostic request: id={} total_us={} query_embedding_us={} query_embedding_calls={} retrieval_us={} retrieval_calls={} answerability_us={} answerability_calls={} generation_us={} generation_calls={} status={} failed_stage={} exception={} root={} http_status={} category={} http_attempts=NOT_OBSERVABLE",
                        id, (System.nanoTime() - start) / 1000, micros(Stage.QUERY_EMBEDDING), counts.getOrDefault(Stage.QUERY_EMBEDDING, 0),
                        micros(Stage.RETRIEVAL), counts.getOrDefault(Stage.RETRIEVAL, 0), micros(Stage.ANSWERABILITY), counts.getOrDefault(Stage.ANSWERABILITY, 0),
                        micros(Stage.GENERATION), counts.getOrDefault(Stage.GENERATION, 0), status, failedStage == null ? "NONE" : failedStage,
                        safe.outer(), safe.root(), safe.httpStatus(), safe.category());
            } finally {
                if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
                if (previousMdc == null) MDC.remove(MDC_KEY); else MDC.put(MDC_KEY, previousMdc);
            }
        }
    }
}
