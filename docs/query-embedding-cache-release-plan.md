# Redis Query Embedding Cache — release precheck and staged plan

## Scope / status (2026-10-09)

Local version freeze and production **read-only** inspection only. No push, production
installation, configuration change, restart, migration, JAR replacement or cache enablement.
This plan does not authorize any of those operations.

The release commit contains this document. Exact candidate JAR SHA-256 and fixed-commit
build evidence are recorded separately in the protected local `target/releases/<commit>/`
directory, avoiding a self-referential commit/hash change. Preserve earlier release artifacts.

## Production observation

Read-only observation at 2026-10-09 14:34 UTC:

- Source HEAD: `bd1b2598c547951ec051f4ef80d0f674755a8900` (not the active JAR's source).
- Active JAR SHA-256: `C06572623F6ED3A06048370FDB4B77186716A9E8323E7DA4E84068D4B2934BB9`.
- Memory: 3719MiB total, 2052MiB available; Java RSS 580140KiB (~566.5MiB).
- Docker: MySQL ~436.2MiB, Qdrant ~38.47MiB; both running.
- Root disk: 38GiB free / 59GiB total; no configured swap.
- No running Redis container, active Redis service, or TCP listener on 6379/16379 found.
- Spring Boot, Nginx and Docker active; backend listens on loopback 8080.
- Document 29 / Canonical 8 / Chunk 466 / EvidenceRef 15 / Qdrant 466, green,
  vectors 1024 / Cosine. No data mismatch.
- Effective `APP_IMPORT_TASKS_ENABLED=false`. Existing JAR has no query-cache feature.
- HTTPS homepage/privacy 200; public /admin/ and /admin/api/ 403; /api/documents/4 404.

There appears to be enough current headroom for a 128MiB-capped Redis plus a small Java
client overhead. This is a point-in-time observation, not a peak-load/OOM guarantee.
No swap means sustained peak memory pressure must be monitored, not hidden by assumptions.

## Code/security review

- Query-only interception; global EmbeddingModel, ingestion and frozen snapshots unchanged.
- Disabled by default; disabled mode creates no Redis connection/client-resource beans.
- Readiness and Qdrant lookup are repeated even on HIT. No response, citation, permission,
  task, source-file or publication-state caching.
- Exact-input SHA-256 key, model/options/endpoint signature, explicit cache/model/input version.
  Same-name remote model-weight changes require an operator model-revision bump.
- Float32 exact storage with dimension/binding/checksum/nonfinite validation. No Java object
  deserialization or cached exception/empty vector. Redis compromise is not solved by checksum.
- Connect/command timeout default 200ms, permitted range 1ms–1s; local backoff 5s.
  Cold client initialization can exceed the network timeout: final prior acceptance measured
  ~657ms cold unavailable-port fallback. No end-to-end 200ms guarantee.
- Redis failure falls back to the original provider; original provider errors remain errors.
- Outcome-only debug logs and exception-class-only warnings; no queries, vectors, credentials.
- Single-flight is bounded to 128 keys in one JVM, not a cross-instance locking guarantee.
- No Redis starter auto-configuration required; Spring Data Redis/Lettuce managed by Boot BOM.
- Secrets are environment placeholders; test credentials are generated or local-stub-only.

## Privacy prerequisite before stage C

Current PrivacyPage explains remote model processing and technical logs but does not disclose
temporary query-vector storage. It remains accurate with cache disabled. Before enabling the
cache, obtain approval for a minimal frontend disclosure such as:

> 为减少重复问题的向量化请求，启用查询缓存时，服务器会短暂保存问题的向量表示和
> 经哈希处理的缓存标识，不在该缓存中保存问题明文、回答或引用。缓存通常约保留
> 24 小时，并加入随机过期时间，最长约 26.4 小时；容量淘汰或服务重启可能使其更早
> 失效。向量表示仍可能包含语义信息，不应视为完全匿名数据。该缓存不作为问答历史档案。

Only use this wording if actual production TTL/jitter/persistence match. Do not promise
third-party deletion, anonymity or zero logs. Configured TTL is per-entry; a later miss may
create a new entry. No production privacy page was changed in this precheck.

## Staged rollout — requires separate authorization

### A. Redis service only, cache still disabled

1. Repeat resource/port/service checks and record the business baseline.
2. Approve a dedicated service/container, image revision and a generated secret stored in a
   root-readable environment file outside Git; do not print compose-resolved secrets.
3. Bind Redis only to 127.0.0.1 (suggest existing local convention 16379), never public 6379.
   Use requirepass/ACL, maxmemory 64mb, allkeys-lru, save disabled, appendonly no, no volume.
   Container process memory cap 128MiB; account separately for client threads/buffers/RSS.
4. Authenticated PING and unauthenticated-denial check; verify actual memory/policy/persistence
   through a whitelisted configuration read, never a full config/environment dump.
5. Spring Boot need not restart. Do not use the local compose file unreviewed as production
   infrastructure. Do not alter Nginx/firewall or existing MySQL/Qdrant/Docker services.

### B. Fixed JAR, cache disabled

1. Back up active JAR and the precise configuration files to a protected directory, verify hashes.
2. Use the fixed-commit clean-build artifact only; server source is older and must not rebuild it.
3. Verify no external source enables cache; keep cache=false and import-tasks=false explicitly.
4. Replace only the approved JAR through the existing safe switch. Restart Spring Boot only;
   expect a brief backend outage until port 8080 and application readiness are confirmed.
5. Verify real answer/Citation once for a known query, Canonical/fallback compatibility, source
   reading, administrator protection and unchanged counts. Do not run the full benchmark.
6. No database migration: this change adds no entity/table/index or Qdrant schema changes.

### C. Explicitly approved cache enablement

1. Complete privacy disclosure above, secret/network review and model-revision verification.
2. Set only approved cache connection/authentication/version/TTL settings and enabled=true;
   keep Evidence Worker false. Configuration is startup-bound: restart Spring Boot only.
3. Observe MISS -> provider call and then HIT -> no additional query embedding call on the
   exact same *actual embedding input*. Query rewriting can make superficially identical
   user questions different embedding inputs; do not change rewriting to force a HIT.
4. Check actual vectors/candidate IDs/Citation and fresh readiness behavior. Every RAG request
   must still retrieve, check readiness and generate; no answer cache is introduced.
5. Monitor hit/miss/error, ratio, model-call count/duration, Redis memory/evictions, Java RSS,
   CPU and request latency. Counts are logical model invocations, not SDK internal HTTP retries.
   Use existing protected/local observability; do not expose a new public metrics interface.
6. A controlled Redis-failure check requires explicit approval; only the dedicated disposable
   cache may be interrupted, never MySQL/Qdrant. Keep checks few and record actual timing.

## Failure / rollback

- Redis unavailable: application automatically uses the provider with bounded connection
  timeouts/backoff. Do not reconfigure the model or weaken retrieval/readiness to recover.
- For sustained errors or resource pressure: approved change enabled=false, restart Spring
  Boot, verify provider path and real Citation. Redis may remain idle until separately approved
  handling; no cache deletion or business-data repair is needed for correctness.
- Retain the new Readiness-Gate-capable JAR when disabling cache. Existing tasks/partial points
  mean a pre-readiness old JAR is NOT a safe general rollback. No worker enablement is part of
  this rollout. Preserve all ImportTask/Snapshot/Document/Chunk/Point data.
- If the new JAR cannot start, use the tested existing hardened aef55da artifact/configuration,
  with import-tasks=false; confirm schema/config compatibility before switching. Never use the
  server's older source HEAD as a rollback build.
- No MySQL or Qdrant restoration, schema rollback, re-embedding, collection rebuild, Gold change,
  full deploy.sh, or Nginx HTTPS/access-control change is necessary.

## Gate decision

Local freeze and resource precheck can pass independently of production enablement.
Production cache enablement remains gated on the approved privacy notice update and an
explicit staged rollout authorization. No real production hit rate/latency gain is claimed.
