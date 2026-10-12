# RAG protection: local implementation and independent production rollout

For the merged Chat Timeout Hotfix candidate, use
[the integration review and rollout plan](rag-protection-hotfix-integration.md).
This earlier protection-only plan and its old artifact are not a deployable
substitute for the merged candidate with the production Hotfix retained.

This is a preparation document, not permission to change production. Both new
flags default to false. No SQL migration, data import, document embedding, Gold
update, Nginx change, or Redis authentication change is required by this code.
The existing local PrivacyPage edit and three benchmark review files are retained.

## Boundaries and compatibility

- Verified source versions: Java 21, Boot 4.1.1, Spring AI 2.0.1, OpenAI SDK 4.49.0.
  Resilience4j 2.4.0 core circuitbreaker/bulkhead/retry modules are used, not a
  Boot 3 starter, annotations, self-invocation proxy, or executor-based TimeLimiter.
- MVC interceptor protects POST `/api/rag/ask` before controller invocation and
  therefore before embedding, Qdrant, Answerability and final generation.
- Chat BeanPostProcessor wraps only the OpenAI ChatModel's synchronous remote
  call; parsing Answerability, DB queries and Qdrant are outside its circuit.
  Answerability propagates protection exceptions instead of classifying them as
  insufficient knowledge. Streaming is not part of current RAG; protected streaming
  explicitly rejects rather than silently bypassing the guard or retrying partial output.
- QueryEmbeddingCache.provider is the query-only embedding boundary. HIT bypasses
  the guard/model call. MISS uses a separate OpenAI query client built from the
  original model options plus Spring AI's resolved common/model-specific connection
  properties. Auto-configured OpenAiEmbeddingModel.toOptions() omits SDK connection
  credentials; copying getOptions() alone is NOT sufficient. Resolution uses the
  same OpenAiAutoConfigurationUtil as the actual SDK autoconfiguration, including
  explicit empty API keys and model-level overrides, without logging their values.
  Model, endpoint, credentials, dimensions and extra-body options are preserved.
  Only transport timeout/retry change. The original global
  EmbeddingModel and import/index-snapshot calls are not wrapped.
- Qdrant's query wrapper is also installed with resilience enabled and cache
  disabled, covering V1 queries. V2's precomputed embedding and Evidence fallback
  continue sharing the prepared vector. Readiness Gate is always downstream and
  unchanged. Neither feature skips live retrieval or caches answers/citations.
- When both new flags and cache are disabled, no protection Redis connection is
  created. Rate limiting can use the existing connection without requiring cache
  enablement; cache and rate limiter use different templates and namespaces.

## Configuration and client identity

All settings below are Spring properties, with standard environment equivalents:

| Property | Local default |
|---|---|
| `app.rag.rate-limit.enabled` | false |
| `app.rag.rate-limit.ip-per-minute` / `ip-capacity` | 10 / 3 |
| `app.rag.rate-limit.global-enabled` | true |
| `app.rag.rate-limit.global-per-minute` / `global-capacity` | 30 / 5 |
| `app.rag.rate-limit.hmac-secret` | `${RAG_RATE_LIMIT_HMAC_SECRET:}`; required when enabled |
| `app.rag.rate-limit.trusted-proxies` | empty list: ignore all forwarded identity headers |
| `app.ai.resilience.enabled` | false |
| `app.ai.resilience.window-size` / `minimum-calls` / `failure-rate` | 20 / 10 / 50 |
| `app.ai.resilience.open-wait` / `half-open-calls` | 30s / 2 |
| `app.ai.resilience.chat-concurrency` / `embedding-concurrency` | 2 / 3 per application JVM |
| `app.ai.resilience.chat-timeout` / `embedding-timeout` | 60s / 60s |
| `app.ai.resilience.extra-retries` / `retry-wait` | 0 / 200ms |

These are starting values, not production optimal values. Enable only after quota
and resource review. Generate a separate high-entropy HMAC secret on the server
and inject using a root-only environment file, not command arguments or Git.
Changing the HMAC secret resets identities/buckets; avoid routine rotation during
traffic without accounting for the temporary renewed burst allowance.

The repository Nginx example overwrites X-Real-IP with `$remote_addr` but appends
X-Forwarded-For. The implementation NEVER uses XFF. X-Real-IP is used only if the
direct socket peer matches the explicitly configured exact numeric proxy list.
Numeric IPv4/IPv6 are canonicalized before HMAC-SHA256; no DNS identity lookup,
raw IP, question or full key is logged. Invalid forwarded values fall back to peer.
Before production enablement verify the actual active Nginx rule, bind backend to
loopback, and confirm `server.forward-headers-strategy=none` and no Tomcat
RemoteIpValve/filter rewrites getRemoteAddr before this check. Otherwise stop:
do not derive trust from an already rewritten address. Loopback proxy trust also
assumes local processes are trusted. Do not enable broad subnet trust.

## Lua behavior and limits

Keys: `nju:rate:{rag}:ip:<HMAC>` and `nju:rate:{rag}:global` (same cluster hash tag).
The constant-time Lua script uses Redis TIME, refills two buckets, and debits both
only when BOTH limits allow. Rejections persist replenished balances without
debiting the other bucket. Results include allow, both remaining token counts,
and ceil(wait seconds); 429 includes RATE_LIMITED and Retry-After. Idle keys expire
after twice full refill time, at least 30 seconds. DefaultRedisScript/RedisTemplate
uses EVALSHA and falls back to EVAL after NOSCRIPT (tested by SCRIPT FLUSH on a
test-owned container only).

Redis faults fail closed with 503 / RATE_LIMITER_UNAVAILABLE, whereas cache faults
still fail open to protected embedding. This intentionally sacrifices public AI
availability during limiter Redis failure. An emergency flag-off rollout requires
explicit approval and model quota/abuse assessment; keep semaphore bulkheads and
independent edge limits if available. Disabling rate limiting does not disable cache.

**Shared allkeys-lru Redis cannot guarantee strict protection under eviction.**
Eviction, Redis restart and HMAC rotation can restore a full burst. Real isolated
memory-pressure tests demonstrate bucket loss. Arbitrary source addresses may
also consume key space; TTL does not bound cardinality under sustained abuse.
For stronger protection, separately approve an independent Nginx limit_req zone
keyed on Nginx's trusted remote address, plus appropriate connection/request-size
limits. Do not apply repository security scripts over the deployed HTTPS config.
Dedicated non-evicting limiter Redis is a future option, not added infrastructure.
Lua atomic execution is not transactional rollback of Redis command errors/OOM.
Errors reject requests, but partially persisted state after an error is possible.

## Remote protection, retries and observability

Circuit names: deepseekChat and queryEmbedding. Semaphore limits are per JVM, not
global cluster concurrency. Bulkhead rejection is immediate AI_BUSY / 503 and is
outside the circuit and retry. OPEN returns AI_CIRCUIT_OPEN / 503. Provider HTTP/IO
errors retain existing stage-specific AI_SERVICE_UNAVAILABLE handling. No fake
answers, vectors, citations or successful business fallbacks are produced.

Spring AI 2.0.1 forwards request timeout but does NOT forward maxRetries in its
per-request RequestOptions. Therefore setting prompt maxRetries alone is NOT
sufficient. The enabled Chat autoconfiguration properties (top-level AND nested
options) are set to SDK retries=0 before client construction. The dedicated query
embedding client's SDK retries are also 0. Disabled mode preserves original SDK
configuration. A real auto-configured Spring ChatModel test sets an adversarial
configured SDK retry count and verifies physical requests stay bounded.

Chat auto-retry is disabled even if extra-retries=1: a lost response may be billable
and cannot be proven safe to replay. Only query embedding may opt into ONE extra
retry, only HTTP 502/503/504; no auth/parameter/429/general 4xx, bulkhead rejection,
network timeout or streaming retry. Circuit statistics count each guarded model
attempt, not every internal HTTP attempt. Client-level real transport timeouts bound blocking calls; no claim of
remote cancellation, total RAG deadline, or exactly-once is made. With opt-in
embedding retry, its worst case is up to two timeout budgets plus backoff. RAG
also has multiple Chat stages, so its total duration may exceed one call budget.
Verify Nginx timeout relationship separately before rollout; do not change it here.
Spring AI's underlying OkHttp transport independently enables connection recovery
(`retryOnConnectionFailure=true`); the public builder does not expose a switch for
that behavior. SDK maxRetries=0 does NOT prove zero transparent transport recovery
or exactly one physical HTTP attempt for every possible network failure. The
503/401/timeout test scenarios measure actual request counts, not a universal
guarantee. Circuit/cache provider counts represent guarded model invocations, not
every internal transport attempt. Default outer retry=0 avoids another retry layer;
optional embedding retry only targets explicit HTTP 502/503/504, not IO failures.
Do not enable optional retry in production without quota and transport review.
No custom HTTP transport or SDK rewrite is introduced to hide this limitation.
Only current OpenAI-compatible sync clients are supported; alternate embedding
providers/custom clients require explicit compatibility testing before enablement.

Safe process-local Micrometer counters/timers: `nju.rag.limit{result=allowed,
rejected,unavailable}`, `nju.ai.calls{model,result}`, `nju.ai.retry{model}`,
`nju.ai.remote.duration{model}` and `nju.ai.circuit.state{model}`. State transition
logs contain only model logical name/state. No query/IP/key/vector/provider body
or secret tags. No public Actuator/export endpoint is introduced. In-process
metrics are not a production dashboard; a protected export would require separate
approval. Remote duration includes retry/backoff and isolation permission holding.

## Independently approved rollout

1. A: freeze/test a clean release artifact; scan secrets, record commit/JAR digest;
   back up active JAR and protected env; deploy approved JAR with BOTH new flags
   false, existing cache true, Evidence Worker false, ddl-auto validate. Restart
   Spring only. Verify real RAG/Citation, Source Reader, admin denial and exact
   historical data/point fingerprints. No migration required.
2. B: separately approve trusted peer/header checks, HMAC injection and limiter
   budgets; review shared-Redis eviction risk and independent edge guard. Review
   privacy disclosure of temporary HMAC-derived network
   identifiers for abuse prevention (not anonymous identity or user profiling).
   Enable only rate limiting; Spring restart needed. Use controlled admission tests that
   reject before AI; avoid high-concurrency real model requests. Check 429,
   Retry-After, safe metrics, Redis memory and existing cache operation.
3. C: separately approve resilience; confirm actual model beans/options and SDK
   zero retries, timeout vs upstream budgets, 2C/4G headroom. Enable resilience,
   restart Spring only, few ordinary questions; use isolated evidence for timeout,
   circuit OPEN/HALF_OPEN and fault cases. Never kill production Redis/providers.

Resource review: reuse existing Redis connection/resources, one additional query
HTTP client only when resilience enabled; no worker pool or infrastructure. The
guard owns this query client and closes it on Spring bean destruction; creation
and shutdown are serialized, and original import/model clients are never closed
by this guard. Its network requests do not hold the lifecycle lock.
The
64MiB eviction budget now includes limiter keys. Observe Redis RSS/OOM, host free
memory, Java RSS, model duration and bulkhead rejects. 128MiB container cap is not
a promise against memory peaks.

Rollback: independently disable the affected flag, retain cache true and Worker
false, restart ONLY Spring. Preserve Readiness Gate and import snapshot version;
never restore a pre-gate JAR. Redis outage can disable expensive public RAG by
design; do not silently switch limiter fail-open. Backups are protected; do not
delete tasks, points, cache, volumes or existing data. Local version freeze is
authorized; production changes and Git push are not. The exact commit/artifact
digest and clean-source build evidence are recorded under the ignored, protected
local `target/releases/<commit>/VERIFICATION.md`, not in this self-referencing file.

## Production read-only precheck / activation gates — 2026-10-10

Observation at 09:16 UTC (no production file, database or Qdrant write):

- Active JAR: `04ac3f8c0d0fb46b6dbe641ced5a780e701431257506aa940e8c773a5d06cbbf`.
  Server source HEAD remains `bd1b2598c547951ec051f4ef80d0f674755a8900`;
  never rebuild the new release from that old source.
- Spring/Nginx/Docker active, MySQL/Qdrant running. Counts remain 29 documents,
  8 Canonical, 466 chunks, 15 EvidenceRefs and 466 points. Table/original-file/
  point-vector-payload fingerprints match the earlier protected stage-C baseline.
  One import task exists and none are QUEUED/PROCESSING.
- Cache true, Evidence Worker false, no new protection flags configured. Redis
  7.4.11 healthy; authenticated PING passes, unauthenticated PING denied, bound
  127.0.0.1:6379, public TCP probe timed out. OOM=false, restart count=0.
- Redis data memory 1,258,520 bytes, RSS 9,285,632 bytes, 4 embedding keys and
  no limiter keys, evicted_keys=0. Each existing vector entry has measured
  MEMORY USAGE 5,320 bytes. Maxmemory=64MiB, container cap=128MiB.
  Current combined usage has headroom, but 64MiB is a shared eviction budget,
  not a reservation for limiter keys or a proven maximum-IP capacity. About
  12k such vector entries would consume most of that budget even before future
  limiter keys/extra overhead; this is arithmetic, not a load-test result.
- Host MemAvailable about 1,948MiB, Java RSS 604MiB, MySQL container 433MiB,
  Qdrant container 40MiB, root disk 37GiB free, no swap. Point-in-time headroom
  is adequate; peak throughput/memory safety has not been pressure-tested.
- Active Nginx overwrites X-Real-IP with `$remote_addr`, appends XFF, and has
  no real_ip_header/set_real_ip_from overrides. Backend listener is the IPv4
  loopback mapped address `[::ffff:127.0.0.1]:8080`, not a public bind. Repository
  has no ForwardedHeaderFilter/RemoteIpValve customization. Configure exact
  trusted peer 127.0.0.1 only after confirming raw remoteAddr at activation;
  explicitly keep server.forward-headers-strategy=none. Local processes remain
  inside the proxy trust boundary.
- Existing edge limit already exists: shared `nju_rag_ask_per_ip:10m`, rate
  10r/m, burst=4 nodelay, status=429 on public RAG paths. Retain it unchanged.
  It supplies an independent backstop against Redis bucket eviction, but not
  a new globally distributed quota guarantee. A public 429 alone does not
  prove Lua rejection: verify RATE_LIMITED JSON plus Retry-After, distinguishing
  existing Nginx rejection from application rejection.
- HTTPS homepage/privacy/source page return 200; admin/admin API 403; old
  document API 404. Existing journal has successful Canonical retrieval markers.
  No new production RAG request/model call was made in this read-only review;
  fresh answer/Citation smoke is mandatory in approved deployment stage A.

Stage A (both new flags false) is technically prepared after the fixed artifact
is built/verified. Stages B/C are not blanket-approved by that result:

1. Before B, approve exact trust/HMAC secret injection, IP/global budgets and
   minimal privacy disclosure for temporary HMAC-derived network identifiers.
   Do not include the pre-existing unrelated privacy edit in the backend commit.
   Limit controlled tests; retain current Nginx protection and distinguish its
   429 from Lua's. Redis error gives 503 intentionally; stop on unexpected
   rejection, eviction pressure or cache errors. No Redis/Nginx tuning here.
2. Before C, settle actual timeout budgets and per-JVM bulkhead capacities.
   Nginx RAG proxy_read_timeout is currently 180s. A cold Canonical miss followed
   by rejected Canonical Answerability, Evidence Answerability and generation
   can make one embedding + three Chat calls. Defaults of 60s each can reach
   240s plus local overhead; they are not a safe aggregate 180s guarantee.
   Keep optional embedding retry=0 and choose separately approved smaller
   budgets (for example embedding 30s/chat 40s gives 150s call budgets plus
   overhead margin), or approve a future end-to-end deadline design. Do not
   change production Nginx to mask this mismatch. The example is a proposal,
   not a validated production latency target or a change applied here.
3. Chat concurrency=2 and embedding concurrency=3 are per JVM, not globally
   coordinated. Chat Answerability and generation share the Chat semaphore;
   saturation produces immediate 503, not queuing. These values need traffic/
   provider-quota approval, not a claim they are optimal for 2C/4G. Validate
   normal traffic only in production; OPEN/HALF_OPEN/timeout/fault proof comes
   from isolated tests, without manufacturing production provider failures.

At each stage, stop on startup, security, data-fingerprint or RAG/Citation
regression. Revert only the affected flag/configuration using its protected
backup and restart Spring, preserving cache=true/Worker=false and readiness.
If the new JAR fails in A, the verified b8279f1 cache JAR is a readiness-capable
fallback after compatibility/data checks. No schema migration is involved.

Primary library reference: https://resilience4j.readme.io/docs/getting-started
