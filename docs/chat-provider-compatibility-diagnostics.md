# Chat Provider compatibility fix and safe RAG diagnostics

Base candidate: `d77902813d3affc776ecdf129c07c3a341390746`.
Production rollback reference: `dfdd8d1c5041c148993d93d01b52ed2dda909cf2`.
This document describes a **local-only** candidate, not permission to deploy or switch providers.

## Proven compatibility issue and minimal fix

The old release resolved unset model-specific connection properties from the bound common
`spring.ai.openai.base-url` / `spring.ai.openai.api-key`. The d779028 packaged aliases instead
fell back directly to `AI_BASE_URL` / `AI_API_KEY`, bypassing common Spring property overrides.
This difference was reproduced against both exact release JARs; it was **not shown to cause**
the 46.474s production I/O failure. That incident's nested cause remains UNKNOWN.

The corrected defaults reference the resolved common Spring properties. In NJUSEHub mode:

1. Explicit `spring.ai.openai.chat.base-url/api-key` wins, including their standard environment bindings.
2. Otherwise the optional `AI_CHAT_BASE_URL/API_KEY` aliases apply.
3. Otherwise inherit the bound common `spring.ai.openai.base-url/api-key`, including environment overrides.
4. Those common properties retain the original `AI_BASE_URL/API_KEY` placeholders.

Ordinary Spring property-source precedence still applies within each property; for example a
command-line value can override an environment value. No custom resolver bypasses Spring binding.
Explicit Chat timeout/retry environment settings continue to override packaged defaults.
Resilience4j's existing deliberate zero-Chat-retry enforcement remains unchanged when enabled.

Embedding uses the equivalent independent model-specific/alias/common fallback order.
Keep common legacy settings as **school** settings. Do not migrate the shared URL/key to official
DeepSeek. Explicitly preserving existing school embedding properties is recommended for a future
official Chat switch; it is not necessary to change any embedding value for this fix.

Official mode remains different by design: `AI_CHAT_PROVIDER=deepseek-official` selects the existing
Chat-only Bean postprocessor, which requires `DEEPSEEK_API_KEY` and the independent official
endpoint/model, rejects incompatible auth/embedding configuration and forces official Chat options.
It never falls back to a school key on missing credentials. `njusehub` does not require an official
key. No automatic provider fallback was added. Existing prompt connection override restrictions,
thinking disabled in official mode, timeout facade and client ownership remain intact.

No dependencies, SQL schema, model defaults, prompts, Answerability rules, thresholds, topK,
Citation logic, Readiness Gate, immutable snapshots or import workers were changed.

## Public request diagnostics

`RagDiagnosticsFilter` instruments only synchronous `POST /api/rag/ask`. It starts before the MVC
Lua interceptor and validation and never reads a client correlation header or body. A random UUID
becomes the request ID and MDC `ragRequestId`; every diagnostic line contains the explicit ID.
The finally boundary removes/restores the ThreadLocal and MDC value, including handled provider
errors and unhandled servlet failures. No new HTTP endpoint, external service or transport
interceptor is introduced. No API response body or error code changes are required.

Each observed stage logs its elapsed/exclusive microseconds, success/error, allowlisted exception
class, allowlisted root class, validated HTTP status and enum category. The final line logs total
application-request time plus per-stage exclusive aggregates and invocation counts:

- `QUERY_EMBEDDING`: the query-cache operation, including HIT/MISS, Redis lookup and any protected
  model call/coalesced wait; it is **not** a count or duration exclusively of remote HTTP calls.
- `RETRIEVAL`: query preparation and candidate/provenance retrieval at existing service boundaries;
  nested embedding time is subtracted, so it is not counted twice. It is not pure Qdrant wire latency.
- `ANSWERABILITY`: the existing Chat sufficiency call, including its protection and transport.
- `GENERATION`: the existing Chat generation and extraction boundary.

Repeated Canonical/fallback stages are aggregated; individual stage lines remain separate. A zero
stage count means no observation, not proof of zero remote work. In particular V1 with both cache
and protection disabled can use the stock VectorStore's opaque internal embedding path: retrieval
is observed, but a separate embedding duration is unavailable. Current production has cache and
protection enabled and therefore uses the instrumented query path. No claim of an end-to-end
deadline is made: total time excludes Nginx/client transport, and all existing HTTP limits remain.

The first failed measured stage is preserved while advice/filter can supply the outer application
exception class. Normal Answerability=false is a successful model invocation and retains the
original safe refusal semantics; it is not labelled a provider or circuit failure.

### Failure classification and privacy

Known categories distinguish connect timeout, read/request timeout, DNS, TLS, connection failure,
reset, EOF, interrupted/socket I/O, upstream HTTP 4xx/5xx, circuit open and bulkhead rejection.
Connect timeout uses a typed `HttpConnectTimeoutException` or a SocketTimeoutException with an
allowlisted connect callsite; no stack is logged. The fixed OS tokens for a connection reset may
be compared internally, but messages are never emitted. Generic InterruptedIOException is not
automatically called a timeout. Unknown classes are `OTHER`, not untrusted class names. Cause
walks are identity-cycle-safe and capped at 32 nodes. Unknown HTTP statuses remain null.
If a socket timeout lacks a known connect/read callsite its phase is TIMEOUT_UNSPECIFIED;
cyclic/truncated cause chains report root NOT_OBSERVABLE rather than inventing a root.

The logger records no arbitrary exception message, Throwable/stack, headers, URL, cache key, IP,
question, source text, answer, vector or credential. It contains only generated IDs, enums,
allowlisted class names, validated statuses and numeric times/counts. No async context propagation
is added; import work without a public-request scope is a diagnostics no-op.

Physical HTTP attempts are always **NOT_OBSERVABLE**. SDK maxRetries=0 and local one-send fixtures
do not eliminate every OkHttp recovery/follow-up possibility. No interceptor is installed to count
attempts, alter cancellation, change timeout, or consume/replace bodies. Existing Redis/guard
aggregate metrics continue to have their original meanings.

## Local verification

New suites:

- `ChatConnectionCompatibilityTest`: actual Spring Bean + wire assertions for legacy aliases,
  public Spring overrides (including no legacy aliases), explicit Chat properties/aliases,
  Chat-only plus independent school embedding, official selection/missing key, independent keys,
  unmodified bge-m3 1024 protocol, explicit timeout/retry precedence.
- `RagDiagnosticsTest`: typed classification, secret/message redaction, unknown class masking,
  nested timing, ThreadLocal/MDC cleanup under success/failure/thread reuse, ignored forged headers,
  unrelated endpoint exclusion and import no-op.
- `RagDiagnosticsHttpTest`: real loopback HTTP/TLS and actual SDK/primary protected ChatModel;
  both provider branches with Canonical/Evidence citations, all four stage observations, EOF/503/
  timeout/TLS, actual OPEN and actual concurrent Bulkhead rejection/permit release.

Existing authenticated Redis continuity, Chat40s real deadline, query timeout, Lua, protector,
MySQL8, Qdrant, delayed worker writes, JVM crash/recovery, Canonical and Readiness tests must all
execute before freezing. H2's existing application-context smoke is not MySQL integration evidence.
The two opt-in benchmark traces and Windows symlink-capability assumption remain the only accepted
skips; no Gold edits or live model probes are part of this task.

Final counts, fixed Commit, JAR SHA-256 and clean-source build evidence are recorded separately in
the immutable release directory's `VERIFICATION.md`; old release evidence is not overwritten.

Final local verification on 2026-10-15: **473 PASS, 0 FAIL/ERROR, 3 existing SKIP** (476 cases,
60 suites). The 44 new cases comprise 9 connection compatibility, 25 diagnostic unit and 10 real
HTTP/TLS/Bean diagnostic cases. Existing MySQL8 19, Qdrant2, frozen joint4, JVM restart1,
Canonical31, Retrieval24 and Readiness6 cases all executed successfully. Official/provider20,
real Redis/provider continuity1, protection integration23, Lua14 and query Redis8 also passed.
The real official-mode 40s HTTP deadline fixture stopped at **40004ms**, one observed fixture request.
No live API call was made. The only skips remain the two opt-in benchmark traces and one Windows
symlink capability assumption. The existing H2 context smoke remains separate, not a MySQL substitute.

An intermediate stricter timeout-phase assertion exposed a legitimate race: the HTTP request
deadline and socket timer may supply different nested I/O exceptions. The final classifier does
not invent a read phase without a known callsite; the real deadline test checks elapsed time and
InterruptedIOException-family evidence while accepting an explicitly unknown phase. No skip or
retry was introduced to hide that difference.

## Production configuration and future Stage A (NOT EXECUTED)

**No new required production configuration keys. No migration.** Diagnostics are narrowly scoped
and use existing INFO logging; no DEBUG, actuator exposure, Redis change or new secret is needed.
Existing supported Chat/embedding/provider keys and their environment names are unchanged.

After separate approval:

1. Recheck fixed candidate hash and current stable dfdd8d1c hash, existing flags, counts and protected
   historical fingerprints. Ensure no import is processing, enough memory/disk and backups usable.
2. Back up the current JAR, systemd definition and environment into a new root-only directory.
   Preserve env 0600 and verify backup hashes without exposing secrets.
3. Leave provider explicitly `njusehub`, model deepseek-v4-pro, Chat40s/SDK0, Query30s/extra0,
   cache/Lua/resilience=true, worker=false; do not inject official key or alter school credentials.
4. Deploy only the independently approved fixed artifact (never server old-source builds), verify
   server SHA and restart only Spring Boot once. No Nginx/infrastructure/data changes.
5. Confirm actual model-only SDK connection selection and readiness, then at most one approved
   Canonical smoke; only on full success at most one approved Evidence smoke. Check route,
   Answerability, nonempty answer and citation-source match, not just200. Use new safe stage logs
   to classify any failure. Do not automatically relax timeouts/retries or switch providers.
6. Verify existing original-reader/security restrictions and historical fingerprints unchanged;
   stop. Official Chat switching is a distinct future approval and privacy check.

### Rollback

If startup/configuration/citation or protection regresses, stop further paid requests. Restore the
verified **dfdd8d1c merged** JAR and its protected environment backup, which retain Readiness Gate,
Chat Timeout Hotfix and the established school model. Preserve worker=false and existing cache,
Lua and resilience flags. Restart only Spring Boot under the deployment authorization, then minimum
health checks; a paid smoke needs the stated request budget. Do not delete ImportTask, Chunk,
Snapshot, Point or cache data. No reverse DDL is needed. Do not restore a pre-gate or pre-hotfix JAR.

This is a rollback plan, not a claim that the newly built candidate was deployed or production
RAG verified. The original Stage A transport root cause remains unresolved; new diagnostics improve
the next separately authorized observation but cannot reconstruct an old missing exception chain.
`SAFE_TO_REDEPLOY=false` until a new explicit production authorization.
