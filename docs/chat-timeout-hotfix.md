# Production-base Chat timeout hotfix (local verification only)

## Scope and source

Base: `b8279f1ab490588eeb45b5b8f4ca9e14ab9e0990`, not the unpublished
`52da4867` Redis Lua / Resilience4j branch. No production connection, model probe,
configuration update, restart, migration, deployment or push is part of this work.
The original workspace's PrivacyPage and three untracked Benchmark review files
are left untouched. No model name, timeout default, provider credentials or RAG
parameters are changed in application.properties.

Versions inspected from actual dependency source: Spring AI 2.0.1, OpenAI SDK
4.49.0 and OkHttp 4.12.0. This is a version-specific workaround, not a generic
claim about other Spring AI versions.

## Root cause and adapter

1. `OpenAiChatAutoConfiguration.openAiChatModel` resolves common/Chat properties
   and configures SDK clients correctly, including timeout and maxRetries.
2. `OpenAiChatProperties.toOptions()` omits timeout; `OpenAiChatOptions` fills in
   DEFAULT_TIMEOUT (60 seconds).
3. `OpenAiChatModel.buildRequestPrompt` selects default options only when Prompt
   options are null; a separate options builder can also supply the unwanted 60s.
4. `buildRequestOptions` sets that timeout on SDK RequestOptions; request-specific
   options override the SDK client's configured timeout.
5. `SpringAiOpenAiHttpClient.newCall` applies RequestOptions to OkHttp's connect,
   read, write and call timeouts. The transport can enforce the intended timeout
   once the right value reaches it.

`ChatTimeoutConfiguration` adds a distinctly named, primary ChatModel facade over
the **original auto-configured OpenAiChatModel**. It uses Spring AI's own resolved
common/Chat properties. The resolved timeout is authoritative for both call and
stream, including separately supplied Prompt options. Deliberately, request-level
timeout overrides are not supported by this adapter: administration controls the
timeout. All other supplied options, messages and Prompt metadata are preserved
using immutable builder copies; no business prompt is edited.

There is still exactly one OpenAiChatModel/SDK client pair, with a second ChatModel
view selected by @Primary. Both actual application consumers (AnswerabilityService
and RagService) inject ChatModel and therefore use the facade. Concrete
OpenAiChatModel injection would bypass it; none exists in application consumers.
The facade creates/closes no client, pool or executor, and does not replace or
change ownership of the original client lifecycle. No bean overrides, global
post-processors, HTTP client rebuilds or Embedding decoration are introduced.
The existing context smoke test explicitly disables real Chat auto-configuration
when using its already existing mocked ChatModel.

## Retry semantics

`spring.ai.openai.chat.max-retries=0` reaches the Chat SDK independently of
Embedding. The Chat adapter does not override maxRetries or add a retry layer.
There is no active Spring RetryTemplate/@Retryable around Chat in this base
version. SDK defaults allow three retries (four attempts) unless configured.

**Zero SDK retries is not exactly one wire send.** The existing Spring AI adapter
sets OkHttp retryOnConnectionFailure(true). Local real HTTP tests distinguish:

- ordinary HTTP 503, SDK retries=0: **one** received HTTP request;
- HTTP 503 with Retry-After: 0, SDK retries=0: **two** requests (OkHttp follow-up).

Connection recovery and redirects are also distinct from SDK retries. They remain
unchanged by this minimal hotfix. OkHttp follow-ups within one Call share its call
timeout; do not multiply the logical Chat call budget by the two observed sends.
This does not prove every connection failure has identical timing or behavior.
Strict single-wire-send protection would require a separately reviewed Chat-only
transport customization (no Embedding change); it is not claimed here.

## Verification

`ChatTimeoutConfigurationTest` uses real Spring auto-configuration, actual SDK
clients and a loopback HTTP server with synthetic tokens/input. No remote provider
requests are made. Only supporting tool-manager, retrieval and repositories are
mocked; ChatModel, AnswerabilityService, RagService and HTTP are real in the RAG
fixture. Both services' injected model identity is checked.

Twelve cases cover: 200ms versus 1s response; separate Prompt options preserving
generation options and not mutating the caller; 2s versus 100ms response; ordinary
503; OkHttp Retry-After follow-up; independent Embedding timeout and cache HIT;
independent Embedding retry count; uppercase environment binding; inherited 90s
common timeout; real Answerability timeout; real Generation timeout; successful
Answerability plus Generation and Evidence Citation.

Fresh full-suite XML, exact counts, timings, clean-commit build log, candidate JAR
hash and archive scan are recorded in the new local `target/releases/<commit>/`
directory. They must not overwrite earlier releases. Container tests use isolated
MySQL 8 / Qdrant / Redis, including real JVM crash/restart, never production.
The existing H2 application context smoke is not counted as MySQL integration.
Existing opt-in Benchmark tests remain skipped and Gold is not changed.

## Budget, not an end-to-end deadline

| Chat timeout, SDK retries=0 | Canonical: 2 sequential calls | Fallback: up to 3 calls |
|---|---:|---:|
| 40s | 80s Chat budget | 120s Chat budget |
| 60s | 120s Chat budget | 180s Chat budget |

These numbers are **Chat-only**, not the RAG request's hard maximum. Canonical
prepares one query vector and reuses it for Evidence fallback; a cache MISS still
adds remote bge-m3 latency and its unchanged retry/timeout behavior. Qdrant,
database readiness/provenance checks, scheduling, serialization and proxy overhead
add time. V1 may search multiple filters; no path obtains a new global deadline.
There is no unified RAG deadline in this hotfix. A cache HIT does not avoid Chat
calls, readiness checks, fresh retrieval or final generation.

At Nginx proxy_read_timeout=180s, 60s x 3 leaves no budget for other stages and is
not recommended. Candidate **40s** is a temporary fail-fast compromise, not a
guarantee against Nginx timeout. An Embedding MISS or network delay can still push
the request beyond 180s. Do not merely increase Nginx timeout.

The user-supplied model-plaza average 77.81s exceeds 40s. The earlier minimal probe
at 5.19s does not measure long-context RAG latency. Forty seconds may cause frequent
timeouts; no failure percentage can be inferred from these two facts. If one
controlled real RAG request times out, stop paid requests and ask for approval to
evaluate a provider-confirmed lower-latency model or a separately scoped overall
deadline design. No alternative model ID is guessed or probed in this task.

## Future production procedure — requires separate approval

1. Verify current JAR/base, health, no running import task, baseline
   Document/Canonical/Chunk/EvidenceRef/Qdrant = 29/8/466/15/466 and fingerprints.
2. Back up current JAR, unit and `/etc/nju-transfer-rag.env` in a timestamped
   protected directory (root-only directory/file permissions); do not print env
   contents or secrets. Preserve existing backup and migration history.
3. Upload only the fixed-commit clean-source candidate and verify its recorded
   SHA-256 on the server. Never build from the server's older source checkout.
4. Preserve Redis connection/password, cache=true, worker=false, ddl-auto=validate,
   all security controls and RAG settings. Change only:

   ```dotenv
   AI_CHAT_MODEL=DeepSeek-V4-Flash
   SPRING_AI_OPENAI_CHAT_MAX_RETRIES=0
   SPRING_AI_OPENAI_CHAT_TIMEOUT=40s
   ```

   These exact environment keys were tested through Spring's environment binding.
   Confirm no higher-precedence command-line/config source overrides them.
5. Only restart nju-transfer-rag.service once; record downtime, wait for actual 8080
   readiness, check startup/timeout/provider exception summaries without secrets.
6. At most one established non-sensitive Canonical smoke. Check HTTP, answer
   semantics, correct Evidence Citation, route and end-to-end elapsed time. If it
   fails, stop. Only after success and acceptable latency, perform at most one
   Evidence fallback smoke, checking PERSONAL authority. Do not rerun Benchmark.
7. Verify Source Reader, public Admin=403, old document API=404, Redis health/cache,
   worker=false and unchanged data/fingerprints. Do not restart other services or
   change Nginx/HTTPS/Redis/MySQL/Qdrant.

### Failure and rollback limitation

Stop further paid requests on startup failure, timeout or semantic regression.
Keep diagnostic exception classes/status/stage, never full prompt/provider body.
The known b8279f1 JAR (SHA-256
`04AC3F8C0D0FB46B6DBE641CED5A780E701431257506AA940E8C773A5D06CBBF`)
retains Readiness Gate and can undo this code change after compatibility checks.
However **code rollback does not prove service recovery**: the old lowercase model
is known unavailable, and the old JAR still has the timeout bug. Do not present a
restored old JAR/environment as a validated RAG availability rollback. Choose
whether to restore code/config based on the actual failure and explicit authority;
avoid repeated restarts or silently returning to the unavailable lowercase model.
Fail-closed 503 is preferable to disabling Answerability or exposing unready data.

No migration, reindex, file import, Gold update, Lua enablement, Resilience4j
enablement, frontend deployment or production change is required by this hotfix.
