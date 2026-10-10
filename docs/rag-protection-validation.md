# Local RAG protection acceptance — 2026-10-10

The initial implementation run made no production connection or Git commit.
The subsequent authorized freeze review performed production READ-ONLY checks
and a local commit; no production deployment/restart/migration/configuration/data
change or Git push is authorized or performed. Existing uncommitted privacy
disclosure and three benchmark review Markdown files are retained; Gold is unchanged.

## Executed results

Java 21 (ms-21.0.11); local Docker Desktop Engine 29.7.2; Testcontainers 2.0.5.
All new HTTP stubs bind loopback; the new limiter Redis uses random test-only
authentication and loopback-only dynamically allocated host port. No production
credentials or services are used.

| Suite | PASS | FAIL | SKIP |
|---|---:|---:|---:|
| Full Maven freeze test suite (373 cases) | 370 | 0 | 3 |
| New Redis Lua integration | 14 | 0 | 0 |
| New resilience HTTP / Spring Bean / client lifecycle tests | 14 | 0 | 0 |
| Cache configuration / independent limiter client | 3 | 0 | 0 |
| Provider failure controller regressions | 17 | 0 | 0 |
| Existing MySQL 8 integration | 19 | 0 | 0 |
| Existing Qdrant integration | 2 | 0 | 0 |
| Existing MySQL/Qdrant frozen evidence integration | 4 | 0 | 0 |
| Existing real JVM restart recovery | 1 | 0 | 0 |
| Existing Redis embedding cache integration | 8 | 0 | 0 |
| Existing Redis/Qdrant/RAG integration | 7 | 0 | 0 |
| Canonical controller/import/first retrieval | 31 | 0 | 0 |
| RAG / Retrieval service | 30 | 0 | 0 |
| Readiness service / retrieval visibility | 6 | 0 | 0 |
| Admin Node tests | 6 | 0 | 0 |

Rows overlap; do not add them to derive the Maven total. Admin 6 tests and Vite
build were repeated and passed. Initial acceptance was 369/0/3. The freeze review
added explicit ownership/close of the dedicated query SDK client, synchronized
creation/shutdown without holding that lock during HTTP calls, and a lifecycle
test proving idempotent close and that the original import model is not closed.
All 373 cases were rerun after this minimal fix, including authenticated,
loopback-only Redis and the real MySQL/Qdrant/JVM recovery suites.
No application source changed after the final full Maven run.

The three skips are the two opt-in benchmark trace tests (not enabled; no
benchmark was run) and the pre-existing Windows symlink capability test.

Commands: `mvnw.cmd -q test`, focused `-Dtest=RateLimiterIntegrationTest`, and
`npm test` / `npm run build` in transfer-rag-admin. Ignored local evidence:
`target/rag-protection-final-maven.log`, `target/rag-protection-redis-final.log`,
`target/rag-protection-freeze-final-maven.log` and Surefire XML reports.
Compilation and git diff --check passed. The exact frozen commit, clean-source
JAR SHA-256 and sensitive-entry scan are recorded under its new ignored
`target/releases/<commit>/VERIFICATION.md`; previous release artifacts are retained.

## What the new tests actually prove

- Real Redis Lua: initial burst, 429/Retry-After, Redis-time refill, independent
  identities, global denial, no debit of the other bucket, TTL and NOSCRIPT reload.
  Fifty concurrent same-identity invocations with capacity three admit exactly
  three. Fake XFF/untrusted X-Real-IP do not change identity; trusted overwritten
  X-Real-IP uses canonical IPv6 and HMAC. MVC rejection cannot reach RagService.
- Shared allkeys-lru limitation: isolated Redis maxmemory is temporarily reduced
  to 2MiB and real memory pressure evicts an exhausted bucket. Its next request
  receives a renewed burst. This is a demonstrated limitation, not strict abuse
  protection under eviction. No production Redis was altered or pressure-tested.
- Redis failure: unavailable limiter gives 503/RATE_LIMITER_UNAVAILABLE; independent
  configuration test uses a real refused connection and verifies cache-disabled
  embedding still follows the original model path when invoked independently.
- Actual Spring AI OpenAI HTTP requests against a loopback server exercise success,
  503 failures, OPEN fast rejection, HALF_OPEN/CLOSED recovery, independent chat
  circuit, 401 non-retry/non-circuit failure, one opt-in embedding 503 retry and
  bounded socket timeout. The default extra retry count is zero.
- Actual Spring AI OpenAiChatAutoConfiguration is tested, not just a mock or a
  decorated Supplier: an adversarial configured SDK retry count is suppressed,
  two failures cause OPEN, and the third invocation reaches no HTTP handler.
- Cache HIT works with the embedding circuit OPEN. A new-query MISS is rejected,
  and neither HTTP request count nor cache provider-call count increases on that
  rejection. Cache vector validation and existing frozen-import regressions pass.
- Bulkhead concurrent saturation returns AI_BUSY immediately, records no provider
  failure in the circuit, and releases its permit after completion. No unbounded
  application execution pool is introduced.
- Controller regression proves a protection rejection during Answerability returns
  a safe 503 without answer/citations, rather than Evidence fallback or fake success.

## Remaining boundaries

- These are local correctness tests, not production quota, latency or 2C/4G capacity
  measurements. Production read-only resource/config/fingerprint checks are in
  the release plan; production remains unchanged and new flags remain default false.
- Trusted proxy addresses and real active Nginx header handling must be verified
  before enablement. The implementation ignores XFF and defaults to trusting no
  forwarded peer. Configure exact loopback peers only after checking raw socket
  address preservation and overwritten X-Real-IP.
- OkHttp transparent connection recovery is distinct from disabled SDK retries and
  opt-in Resilience4j retry. The measured HTTP counts apply to test scenarios, not
  a universal exactly-one-network-attempt assertion.
- Only current synchronous OpenAI-compatible AI paths are protected; streaming is
  explicitly unsupported when the wrapper is enabled. Query embedding gets a
  separate client with copied original options; alternate SDKs/custom interceptors
  require compatibility review before enabling. Existing imports use the original
  embedding model and are not cached or reconfigured.
- Metrics are process-local Micrometer registries and safe circuit transition logs;
  no unauthenticated monitoring export was added. Semaphore budgets are per JVM.
- The two new flags can be disabled independently without disabling embedding cache,
  Readiness Gate or immutable import snapshots. No schema migration is required.

See [release plan](rag-protection-release-plan.md) for separately approved rollout,
resource checks, privacy/IP disclosure review, independent edge-limit recommendation
and safe flag-off rollback.

## Freeze review gates

- Production JAR digest, 29/8/466/15/466 counts and protected earlier table/
  point/vector/payload/original-file fingerprints matched. Services healthy;
  current Redis cache enabled and Evidence Worker disabled. No active import.
- Active Nginx has overwritten X-Real-IP, loopback upstream, existing independent
  10r/m edge limit, and no real-IP rewrite directives. No production external
  Spring config file/profile/cloud-platform/forwarded-header CLI override was
  found. IPv4-mapped loopback listener is preserved.
- No new RAG request was sent during this read-only check. Recent existing
  Canonical retrieval journal markers do not replace a future deployment's
  fresh answer/Citation smoke.
- Stage A can preserve both protection flags=false and cache=true. Limiter
  activation remains gated on HMAC/proxy/quota/privacy approval. Resilience
  activation remains gated on aggregate timeout vs existing 180s proxy budget
  and per-JVM concurrency review. No blanket production enablement is implied.
