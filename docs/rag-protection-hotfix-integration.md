# Chat Timeout Hotfix + RAG protection integration

This is a local release review, not authorization to deploy. No production SSH,
paid AI requests, configuration changes, data writes, push or service restarts
are part of this task. Earlier release artifacts/reports remain unchanged.

## Source ancestry and scope

- Common ancestor: `b8279f1ab490588eeb45b5b8f4ca9e14ab9e0990`.
- Target baseline: `e65b59cc0393d505c7b7182cb30643028e3841cd` (production Hotfix).
- Cherry-picked in order: `9e741aa` (Lua/resilience) and `52da4867eb806e2d1e552de47bd5e887fa1b1fee`
  (resolved embedding connection settings).
- Independent branch/worktree: `integration/rag-protection-hotfix`,
  `D:/Codes/Transfer-RAG-Platform/Transfer-RAG-protection-integration`.
- No textual cherry-pick conflict. Semantic Bean assembly conflicts were fixed
  locally. The original PrivacyPage change and three benchmark reviews remain
  only in the original workspace; Gold, migrations and import implementations
  are not changed by this integration.

## Bean and transport audit

The former resilience BPP replaced the concrete `OpenAiChatModel` with an
anonymous `ChatModel`. That cannot satisfy the Hotfix's concrete dependency.
It also overwrote configured Chat timeout with the resilience default of 60s.

The merged chain is now:

`RagService / AnswerabilityService -> [optional guard] -> primary timeout facade
-> original OpenAiChatModel -> original SDK HTTP clients`.

The BPP decorates only bean `timeoutConfiguredChatModel`, not the original
auto-configured model. No additional Chat model or HTTP client is created.
There is no dynamic proxy, additional executor or new Spring AI retry layer.
The facade configures a copy of the Prompt/options for call and stream. With
protection enabled, the timeout is `min(resolved Chat timeout, protection ceiling)`.
Thus the current 40s remains 40s with a default 60s ceiling. With protection off,
the Hotfix's original resolved timeout is retained verbatim.

Resilience enables zero SDK Chat retries before auto-configuration builds its
client. Resilience Chat retry attempts remain one. Query embedding retries are
separate, zero by default, and optionally one extra only for 502/503/504. OkHttp
503/Retry-After follow-up remains distinct from SDK retries; no absolute
single-HTTP-send or end-to-end RAG deadline is claimed.

### Client lifecycle

Inspected actual Spring AI 2.0.1 sources: its Chat auto-configuration privately
creates sync/async SDK clients, and `OpenAiChatModel` is final, not closeable.
A destruction-aware BPP now registers those two clients on the original
`openAiChatModel` and closes each exactly once. Neither facade owns/closes them.
The registry removes ownership before closing; repeated callbacks are no-ops,
and a failure closing one client does not skip the other. No replacement client
is created. A focused spy test covers facade non-ownership and idempotent close.

This adapter intentionally depends on the pinned Spring AI private field layout
(`openAiClient`/`openAiClientAsync`). An incompatible upgrade fails Bean startup
instead of silently leaking clients; upgrading Spring AI requires re-audit.
The query-only embedding guard separately owns its dedicated SDK client and
closes it idempotently. Import embeddings remain the original global model.

## Cache, Lua and visibility

- Query cache HIT bypasses remote embedding protection and remote model calls;
  MISS alone goes through the query guard. No response/Citation cache is added.
- One Redis connection configuration is reused by separate templates/namespaces.
  Credentials use `app.cache.embedding.redis.password=${NJU_REDIS_PASSWORD:}`.
- Limiter uses Redis TIME, single atomic two-bucket Lua execution, HMAC identity,
  429/RATE_LIMITED/Retry-After; Redis failure returns 503. Cache Redis failures
  remain fail-open. No clear, MONITOR or production Redis access is needed.
- allkeys-lru eviction/restarts can reset buckets. This is not an absolute quota;
  TTL also cannot bound key cardinality under arbitrary identity pressure.
- Only explicitly trusted direct peers may supply X-Real-IP. Ignore XFF.
  Confirm Nginx overwrites X-Real-IP and forwarded-header rewriting is disabled
  before enabling trust; loopback trust assumes trusted local processes.
- Readiness Gate, retrieval thresholds/topK, Canonical/Evidence routing,
  Citation construction, worker, frozen snapshots and Gold are unchanged.

## Test evidence and boundaries

`ProtectionHotfixIntegrationTest` uses a test-owned authenticated loopback Redis
container, actual Spring AI auto-configuration, actual SDK HTTP transport, and
a synthetic loopback HTTP server. Its Qdrant client and retrieval repository are
fixtures, not claimed as real Qdrant coverage. Real Redis/Qdrant/RAG and real
MySQL/Qdrant/import/restart coverage come from the separately rerun existing
integration suites.

Four Lua/resilience flag combinations each verify: 200ms versus a delayed 1s
response (transport warmed to exclude class loading), successful 2s timeout,
actual RagService Answerability/Generation injection and Citation, real Redis
MISS/HIT, zero SDK retries on ordinary 503, and Generation-specific timeout.
Additional tests cover 40s configuration and tighter protection ceilings,
production-shaped environment overrides without an HMAC secret when disabled,
normal business refusal versus circuit failures, actual in-flight HTTP bulkhead
rejection, pre-model 429 and Redis fail-closed/fail-open separation.

Full-suite counts, skipped-case reasons, release Commit, artifact SHA-256 and
sensitive-entry scan are recorded in the release `VERIFICATION.md` under
`target/releases/<fixed-commit>/`; no provider benchmark is run.

Final local run on 2026-10-12: 411 discovered, **408 PASS / 0 FAIL / 3 SKIP**.
MySQL 8: 19; Qdrant: 2; frozen snapshot/delayed-write MySQL+Qdrant: 4;
real JVM termination/restart: 1; Redis cache: 8; Lua: 14; Redis/Qdrant/RAG: 7;
Hotfix: 13; new four-combination suite: 23; resilience: 16;
Canonical: 31; RagService/RetrievalService: 30. No new integration skip.
The three existing skips are two opt-in paid/provider benchmark traces and the
Windows symlink capability test. Admin: 6/6 PASS, build PASS, npm audit zero.
Initial development failures (cold transport timing and SDK close-interface
assumption) are retained in ignored logs; both were fixed and the entire suite
rerun after the last Java change, not hidden or counted as passing attempts.

The application context smoke still uses its pre-existing H2 fixture; **all
MySQL concurrency/transaction/migration/restart acceptance uses real isolated
MySQL 8**, never H2 as a substitute. Model HTTP servers use synthetic data only.

## Local production-configuration compatibility

Reviewed from local code and supplied production baseline, not newly queried
from production. Stage A must explicitly preserve:

| Environment key | Value |
|---|---|
| `AI_CHAT_MODEL` | `deepseek-v4-pro` |
| `SPRING_AI_OPENAI_CHAT_TIMEOUT` | `40s` |
| `SPRING_AI_OPENAI_CHAT_MAX_RETRIES` | `0` |
| `APP_CACHE_EMBEDDING_ENABLED` | `true` |
| `APP_IMPORT_TASKS_ENABLED` | `false` |
| `APP_RAG_RATE_LIMIT_ENABLED` | `false` |
| `APP_AI_RESILIENCE_ENABLED` | `false` |

Environment overrides packaged defaults; retain existing AI_BASE_URL/API key,
bge-m3, Redis host/6379/password and Canonical-first override. No new SQL
migration. Existing ddl-auto=validate, readiness and snapshots remain required.
An absent HMAC secret must not block disabled limiting. The disabled resilience
guard creates only local state/metrics, no new model connection/thread pool.

## Separately approved future rollout

### Stage A: merged code, both protections off

1. Obtain explicit deployment approval; fix Commit/artifact hash from verification.
2. Recheck actual active Hotfix hash, health, tasks and 29/8/466/15/466 baseline
   plus historical MySQL/Qdrant fingerprints. Stop on unexplained changes.
3. Back up active Hotfix JAR, systemd and environment into root-only storage;
   verify previous artifacts/data backups. Never build from old server source.
4. Keep both new flags explicitly false and all table/model/cache settings above.
5. Verify uploaded merged artifact hash, switch only JAR, restart Spring only.
   No deploy.sh, Nginx/HTTPS changes, container restarts or DB writes.
6. Validate startup, 40s/zero-retry binding, authentic admin via SSH Tunnel,
   public 403/old API 404/Redis isolation, Source Reader including private 29,
   one real Canonical and one Evidence fallback answer plus Citation (not just
   HTTP 200), cache operation, no limiter keys, and unchanged fingerprints.
7. On startup/code regression restore the backed-up **Hotfix** JAR and required
   config, retaining V4-Pro/cache and worker=false. Never restore a gate-less JAR.

### Stage B: Lua only, separate approval

Verify a root-only HMAC secret, exact trusted proxy chain and backend loopback,
quotas, edge limits, privacy disclosure, Redis budget/eviction and failure policy.
Configure only approved values; keep resilience off. Spring restart is required
for configuration binding. Use a small agreed allowance/429/Retry-After check,
not a load test; confirm rejection reaches no model. Failure: disable only Lua,
Spring restart with explicit approval, verify cache/RAG/security; no key deletion.

### Stage C: resilience, separate approval

Review per-JVM Chat=2/queryEmbedding=3 bulkheads, independent circuits and model
quota. Default extra retries=0. Keep Chat 40s/SDK zero; optional protection
ceiling cannot extend 40s. Canonical normally has two sequential Chat calls,
fallback up to three: 80s/120s Chat-only budgets, excluding embedding/DB/Qdrant,
network and application overhead, against Nginx 180s. This is not a hard overall
deadline. Check existing real-provider behavior before enabling. No live fault
injection, provider outage or pressure tests. Failure: disable resilience only,
Spring restart and verify original model/cache path; do not change timeout or
retrieval rules automatically. Preserve Readiness Gate in every rollback.

Each stage stops after acceptance and requires independent authorization for
the next. No production change is executed by this document.
