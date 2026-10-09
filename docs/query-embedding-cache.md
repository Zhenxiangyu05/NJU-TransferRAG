# Query Embedding Cache — local implementation

## Boundary and correctness

Only query embeddings are cached. No answers, citations, publication readiness, permissions,
tasks, original documents, or index snapshots are cached. `RetrievalService`, `RagService`,
Answerability, Entity Resolution, filtering, topK, and thresholds are unchanged.

- V1: when enabled, construct `QdrantVectorStore` using the **same builder settings as Spring AI
  2.0.1 auto-configuration**, but a private `QueryOnlyEmbeddingModel` delegates its single-string
  query overload to the cache. Batch/document overloads and dimension discovery delegate to the
  original model. It is **not** a global EmbeddingModel bean.
- V2: `QdrantPrecomputedVectorSearch.embed` uses the same cache. Canonical search and Evidence
  fallback still reuse one prepared vector.
- Disabled: stock V1 Qdrant auto-configuration remains active; no Redis connection/resources
  beans are created. V2 delegates directly to the original model through the disabled cache.
- Frozen Evidence indexing and other ingestion paths retain the original model and are not cached.
- A HIT still executes Qdrant search, MySQL readiness checks, Answerability and final generation.

## Identity, encoding and invalidation

Key: `nju:embedding:{version}:{modelSignature}:{sha256(exactEmbeddingInput)}`.
The input is the exact string at the model invocation boundary, after existing rewriting; no trim,
case folding, Unicode normalization or question-text keys are introduced.

Signature includes cache version, explicit model revision, input-contract version, configured
dimension, model implementation, actual OpenAI model/options dimension, and provider endpoint
identity. Endpoint identity is hashed, never logged. Actual model options take precedence over
the older configuration alias. **Bump `model-revision` when a provider changes weights behind the
same model name, or when any embedding-affecting provider/custom-model option changes.** Such
undetectable remote changes cannot be inferred automatically. Bump `version`/`input-version` for
cache format or preprocessing changes. Old keys expire naturally; no SCAN/delete is required.

Values contain exact float32 bytes, dimension, model/input hashes and a SHA-256 checksum.
Unexpected lengths, zero vectors, non-finite values, identity mismatch and corruption are rejected.
The checksum detects corruption; it is not authentication against a compromised Redis operator.
Invalid cache entries trigger fresh embedding and can be overwritten by a valid result. Invalid
provider responses/errors are never stored or silently swallowed.

Default TTL is 24h with +/-10% jitter. Neither empty nor exceptional results are cached.
Redis read/write failure falls back to the original model. Connection/command timeout defaults to
200ms, with a 5s local error backoff avoiding repeated read/write timeouts. Client initialization,
DNS and scheduling can add overhead: 200ms is **not** a hard end-to-end latency guarantee.

Concurrent identical queries coalesce in one JVM using at most 128 in-flight entries. When full,
requests use the original provider rather than an unbounded queue. This is not a distributed lock:
different application instances can compute the same miss. Provider-side retries are unchanged.

## Metrics and privacy

Micrometer: `nju.embedding.cache{result=hit|miss|error|coalesced}`,
`nju.embedding.cache.hit.ratio`, `nju.embedding.provider.calls`, and
`nju.embedding.provider.duration`. `QueryEmbeddingCache.stats()` exposes local counters/ratio
for tests and diagnostics; use an existing MeterRegistry if available, otherwise an internal one.
No new public metrics/admin endpoint is introduced. Outcome-only DEBUG logs and rate-limited
exception-class-only WARN logs never contain queries, vectors, credentials or provider bodies.

Hit rate = successful initial cache lookups / (initial hits + misses); coalesced requests are
separate. Provider calls count logical model invocations, not hidden HTTP retries or document
indexing/dimension probes. A real local HTTP stub test separately verifies physical API calls.

Hashed keys do not make embeddings anonymous: vectors still encode query semantics. Keep Redis
authenticated, network-restricted and disposable; update the privacy notice before any production
rollout to accurately disclose temporary query-vector storage. Do not log or export cache values.

## Optional local Redis (not production deployment)

Check Docker/port ownership first. `compose.redis-cache.local.yml` is standalone and is not used
by the production deploy script. It has no volume, AOF or RDB persistence. Port 16379 binds only
127.0.0.1, requires a caller-supplied `NJU_REDIS_PASSWORD`, uses `maxmemory=64mb`,
`allkeys-lru`, a 128MiB process limit and 0.25 CPU. Do not put the password in Git/command history.

Set the password in the current PowerShell process using a secure prompt (never print it), then:

```powershell
docker compose -p nju-query-cache-local -f compose.redis-cache.local.yml up -d
```

Use only local configuration to enable `app.cache.embedding.enabled=true`; the committed default
is false. Keep `APP_IMPORT_TASKS_ENABLED=false`. No application instance is required to run tests.
Before starting any development application, check port 8080 and use isolated data/configuration.
Do not run cleanup against other projects or their volumes.

## Verification and limitations

Unit tests cover exact input/model identity, MISS/HIT, corruption/dimensions, provider errors,
Redis failures, bounded same-query single flight, TTL jitter, disabled mode and import isolation.
Real authenticated Redis tests cover TTL expiration, reconnecting cache objects, cache loss after
test-owned Redis restart, invalid data, unauthorized access, actual HTTP embedding counts and a
closed-port outage. Real Redis + Qdrant tests compare vectors, candidate IDs, scores and Citation
across V1, Canonical and fallback paths; they also change readiness after a cache HIT.

All fixtures are synthetic. Chat is stubbed, and latency comparison uses a deterministic embedding
stub with a deliberate 30ms delay. These measurements prove integration/call reduction, **not**
real bge-m3 latency, production hit rate or end-to-end LLM speedup. Integration tests use only
their own Testcontainers resources and never production URLs or existing project volumes.

Production enablement requires separate approval, resource/Redis authentication review, model
revision verification, privacy disclosure and small live measurements. Redis capacity is 64MiB
of Redis-managed data, not whole-process RSS; budget at least the separate 128MiB process limit
and measure actual host headroom. Eviction/restart changes only performance, not business truth.

## Local acceptance — 2026-10-09

Environment: Windows, Microsoft OpenJDK 21.0.11, Docker Desktop Engine 29.7.2,
authenticated Redis 7.4, Qdrant 1.19.1 for new integration cases. Existing regression
containers retain their existing MySQL/Qdrant versions. No production services were contacted.

The full suite ran against a source export without `application-local.properties` or `.env`.
All main/test sources, the POM and tracked resources matched the working tree by SHA-256.
Command (set JAVA_HOME to Java 21 first):

```powershell
.\mvnw.cmd -f target/query-cache-validation-20261009/pom.xml test
```

Final result: **340 PASS, 0 FAIL, 0 ERROR, 3 SKIP** (343 total, 2m33s).
New coverage: 14 cache unit, 2 configuration, 8 real Redis, 7 real Redis/Qdrant/RAG
fixture cases; all 31 executed and passed. Existing Canonical 31, RAG/Retrieval 30,
readiness 6, MySQL task integration 19, frozen-index integration 4, Qdrant indexing
integration 2, and real JVM restart 1 passed within the full suite.
Skipped: the two opt-in benchmark trace cases, and the existing symlink test because
this Windows host does not allow symlink creation. These are not counted as passes.

The initial full run failed three Redis cases after a shared test container restart
invalidated its mapped port. The restart fixture now owns a separate container,
re-inspects its actual port and requires authenticated PING before asserting recovery.
The final full run above includes this correction.

Evidence is kept locally under `target/query-cache-full-final.log` and
`target/query-cache-validation-20261009/target/surefire-reports/`.
No production vectors or private query corpus are added to the repository.

### Controlled measurements (not production performance)

Each arm issues the same query three times. V2 executes Canonical and Evidence search
with the same prepared vector in both arms. Values below are measured **sum latency
for three retrievals**, including cold initialization; no statistical speedup is claimed.

| Path | Provider calls off/on | Total ms off/on | Cache hits/misses |
|---|---|---|---|
| V1 | 3 / 1 | 163.996 / 121.785 | 2 / 1 |
| V2 prepared Canonical + Evidence | 3 / 1 | 157.241 / 152.137 | 2 / 1 |

Both arms use synthetic vectors and a deliberately 30ms-delayed model, real local
Redis/Qdrant and mocked database fixtures. Controlled hit rate is 66.7%; real traffic
hit rate and remote bge-m3 benefit remain unmeasured. Exact vectors, candidate IDs,
scores and Evidence Citation identity were asserted equal. Chat was invoked for
every RAG request, including cache hits, and a readiness change still hid Evidence.

A real local HTTP endpoint plus the actual OpenAI embedding client observed two
identical requests causing one HTTP embedding call; changed input caused one additional
call. This proves request elimination without contacting the real provider.
The final closed-port test measured 656.746ms for cold fallback (150ms test client
timeout); initialization adds overhead, so command timeout alone is not a total SLA.
The following request bypassed Redis during backoff and still invoked the model.

Local implementation only: default cache flag remains false. No commit, push,
production migration, rollout or business-data change was performed.
