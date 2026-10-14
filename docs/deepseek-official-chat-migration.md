# Official DeepSeek Chat candidate — local verification and future migration

Base: `dfdd8d1c5041c148993d93d01b52ed2dda909cf2`. This change is local only; no production approval is implied.

## Connections and unchanged behavior

`ChatProviderConfiguration` adjusts only Spring AI's bound `OpenAiChatProperties` before auto-configuration constructs its SDK clients. It never assigns official credentials or endpoints to common or embedding properties. The existing timeout facade remains the primary ChatModel; Resilience4j decorates that same facade. Answerability and generation retain their existing injection, prompts and citation logic.

| Connection | Configuration | Credential |
| --- | --- | --- |
| Legacy NJUSEHub Chat (default) | `AI_CHAT_PROVIDER=njusehub`; `AI_CHAT_BASE_URL` falls back to unchanged school `AI_BASE_URL` | `AI_CHAT_API_KEY` falls back to unchanged school `AI_API_KEY` |
| Official Chat | `AI_CHAT_PROVIDER=deepseek-official`; default `https://api.deepseek.com`; `DEEPSEEK_CHAT_MODEL=deepseek-flash` or `deepseek-v4-pro` | **Only** `DEEPSEEK_API_KEY` |
| School embedding, both query and document import | `AI_EMBEDDING_BASE_URL`, falling back to unchanged school `AI_BASE_URL`; `AI_EMBEDDING_MODEL=bge-m3` | `AI_EMBEDDING_API_KEY`, falling back to unchanged school `AI_API_KEY` |

Never change the shared `AI_BASE_URL` / `AI_API_KEY` to official DeepSeek values. Before any future production migration, explicitly pin `AI_EMBEDDING_BASE_URL` and `AI_EMBEDDING_API_KEY` to their existing effective school values in the protected environment file. Preserve every existing embedding model option, timeout, retry, header and request protocol setting. The migration adds no `dimensions` parameter to the original school request. Expected returned vector length and existing cache validation remain 1024.

Official mode rejects a missing official key, the official endpoint or key on the embedding connection, a non-bge-m3 embedding model, changed dimensions, inherited alternate authentication, and unsupported official model/endpoint. It rejects per-prompt connection overrides. Only loopback HTTP endpoints are additionally accepted for isolated HTTP fixtures; production must use the default official HTTPS endpoint.

The official Chat facade forces `thinking.type=disabled`, the selected official model, SDK maxRetries=0 and the existing authoritative timeout on each request, including prompts with separate options. It preserves structured-output/generation options and does not mutate callers' Prompt options. Actual wire JSON is asserted in HTTP tests. `maxRetries=0` disables SDK logical retries, not every possible OkHttp connection recovery or HTTP follow-up; the existing local test for `503 Retry-After: 0` still documents that distinction.

Query cache keys continue to depend on school model, endpoint, options and input; the Chat provider/model/key do not enter their signature. Cache HIT still bypasses query embedding and its protector. MISS uses the protected school client. `FrozenEvidenceIndexer` still injects the original school EmbeddingModel and freezes the original vectors; it never uses the query cache facade. No schema migration, document import, vector rewriting or Qdrant collection change is needed.

## Isolated acceptance

- `ChatProviderIsolationTest`: real Spring AI auto-configured SDK, two distinct loopback providers, separate synthetic keys; original query and actual FrozenEvidenceIndexer HTTP paths; 1024 dimensions; unchanged request options; Chat thinking JSON; provider rollback; startup denial for crossed connections; prompt override denial; real scaled HTTP timeout; SDK retries; OPEN and bulkhead behavior; actual Answerability / RagService Canonical, Evidence fallback and safe refusal.
- `OfficialChatRedisIntegrationTest`: authenticated isolated Redis container, identical cache key/vector across an NJUSEHub → official Chat context switch; HIT works with query circuit OPEN and makes zero new remote embedding calls. No business data is copied into this container.
- Existing full Maven suite rechecks Redis Lua, cache failure fallback, MySQL 8 concurrency/transactions, Qdrant stable writes, delayed worker writes, JVM restart, Canonical, retrieval and readiness. Existing opt-in benchmark skips remain; no new skip is added.

The HTTP fixtures are synthetic. They demonstrate routing, key separation and compatibility mechanics, not official model semantic quality or real provider latency.

Final local run on Java 21 / Docker Engine 29.7.2: `./mvnw.cmd -q test`, **429 PASS, 0 failures/errors, 3 existing SKIP** (432 cases, 57 suites). The skips are the two opt-in benchmark trace tests and the existing Windows symlink-permission test. No production service is used by this run. The existing H2 context smoke is not counted as MySQL integration evidence; the MySQL counts below ran on isolated MySQL 8 containers.

| Evidence | PASS |
| --- | --- |
| Chat provider/key isolation, real HTTP and synthetic RAG | 20 |
| Official Chat switch + authenticated real Redis continuity | 1 |
| Existing Chat Timeout Hotfix | 13 |
| Protection/Hotfix/Redis bean integration | 23 |
| Resilience4j guard | 16 |
| Redis Lua integration | 14 |
| Query Redis integration | 8 |
| Redis/Qdrant/RAG integration | 7 |
| MySQL 8 import integration | 19 |
| Qdrant import integration | 2 |
| Frozen/delayed-write MySQL/Qdrant integration | 4 |
| Real JVM restart recovery | 1 |
| Canonical service/import/controller combined | 31 |
| RetrievalService | 24 |
| Evidence readiness service/retrieval | 6 |

The official-mode, protected Chat HTTP request configured to 40s timed out after **40.006s**, with exactly one request observed by the isolated delayed HTTP server. The 200ms scaled deadline and normal successful request tests also passed. No school embedding request was sent by that timeout case.

## Approved live probe budget and key handling

No live request is part of the Maven suite. `scripts/Invoke-OfficialDeepSeekProbe.ps1` performs at most one attempt per approved model in this worktree, records an attempt marker before sending, uses a single non-streaming POST with `max_tokens=256`, `thinking.type=disabled`, a 40s total HttpClient timeout, no SDK retry and no redirect. Only a short synthetic instruction is sent. No knowledge base content is read or sent.

It reads `DEEPSEEK_API_KEY` from process environment, or the local Windows user environment if explicitly supplied there. If absent, no HTTP request occurs. Optional `scripts/Set-DeepSeekProbeKey.ps1` uses masked interactive input, never a key argument; the User environment is not a secrets vault. After the two approved probes, the user can remove that value with `-Remove`. No script automatically reads application-local properties, Codex configuration or production secrets.

Only reviewed status/usage/timing metadata is saved beneath ignored `target/official-probes/`. Response text, reasoning and headers remain in memory. TTFT is NOT_MEASURED for these non-streaming probes. Peak USD cost is an upper estimate, not a billing confirmation; actual rates depend on provider caching and peak/off-peak windows.

The two authorized official probes executed on 2026-10-14; no retry or extra paid request was sent:

| Requested / returned model | HTTP | Total latency | Content | Finish | Input / output tokens | Peak upper cost estimate USD |
| --- | --- | --- | --- | --- | --- | --- |
| deepseek-v4-pro | 200 | 1.303s | Nonempty | stop | 9 / 1 | 0.00001584 |
| deepseek-flash | 200 | 0.730s | Nonempty | stop | 9 / 1 | 0.00000390 |

Both requests explicitly disabled thinking. The response did not report reasoning token counts, so do not invent them. No TTFT, complete RAG latency or real-evidence answerability quality was measured.

Official references checked 2026-10-14:

- https://api-docs.deepseek.com/quick_start/pricing/
- https://api-docs.deepseek.com/zh-cn/api/create-chat-completion/
- https://api-docs.deepseek.com/zh-cn/guides/thinking_mode/

The price page lists Flash/Pro peak USD per million tokens as input miss 0.30/1.32, input hit 0.006/0.044, output 1.20/3.96; off-peak is half. These are dated observations, not a permanent guarantee. Default thinking is enabled on the provider, hence explicit disabling is required.

## Performance evidence limits

Existing `nju.embedding.provider.duration` measures query provider time, `nju.embedding.cache` counts HIT/MISS, and `nju.ai.remote.duration{model=deepseekChat}` measures protected Chat calls. Evaluation trace captures total RAG time in local synthetic regressions. Existing metrics do not separate Answerability from Generation or isolate Qdrant duration; do not report invented stage timings. No production benchmark or additional metrics endpoint is introduced.

Candidate preference is Flash with thinking disabled for cost/latency evaluation, Pro with thinking disabled for quality evaluation. A single minimal live completion cannot establish RAG answerability/citation quality, steady-state latency, P95 or end-to-end improvement. No separate Answerability/Generation routing is added. Existing 40s per-Chat and protected 30s per-query embedding budgets remain: nominal one-embedding Canonical 110s, three-Chat fallback 150s, excluding other work and possible transport follow-ups. Neither is an end-to-end hard deadline.

## Privacy disclosure to prepare before future production migration

The existing page already discloses remote question vectorization, evidence sufficiency checks and generation, but does not identify the new official recipient. Preserve the user's existing uncommitted cache/rate-limit/privacy edits in the main worktree. Prepare this minimal replacement for the existing remote-provider paragraph; do not publish it in this task:

> 用户提交的问题会发送到本站后端。问题向量化继续由学校提供的远程 bge-m3 服务处理；在启用 DeepSeek 官方 Chat 服务时，问题及回答所需的检索资料片段会发送至 DeepSeek 官方 API，用于资料充分性判断和回答生成。当前应用的数据模型中未发现用于保存用户问题或问答历史的表，也没有面向用户的历史记录功能；这不等于请求不会经过第三方服务。第三方服务如何处理或保存请求数据由其自身规则决定，本站无法代其承诺具体留存期限。

This does not promise anonymity, zero retention, or permission to publicly redistribute private original documents. Real local probes never send PERSONAL evidence.

## Future production migration — separate authorization required

1. Verify the accepted release commit/hash, current production health and 29/8/466/15/466 baseline/fingerprints. Do not rebuild from the server's old source.
2. Back up the active JAR, systemd definition and environment file into a timestamped root-only directory; preserve environment mode 0600. Confirm rollback JAR has Chat Timeout Hotfix and Readiness Gate.
3. Publish the separately reviewed privacy disclosure before enabling the official recipient.
4. Pin the exact existing school embedding endpoint/key to the independent embedding keys without changing their values. Retain all school/common variables. Safely inject `DEEPSEEK_API_KEY`; never print it or put it in process arguments.
5. Set `AI_CHAT_PROVIDER=deepseek-official`, selected `DEEPSEEK_CHAT_MODEL`, Chat timeout 40s and SDK retries 0. Keep query protection timeout 30s/extra retries 0, Lua/cache/resilience true, Evidence Worker false. No migration, Nginx change or Redis/MySQL/Qdrant restart.
6. Upload the fixed accepted JAR, verify SHA-256, and restart only Spring Boot once. Check client/Bean initialization and the effective connections without exposing secrets.
7. Only under a new production test budget, verify one Canonical and one Evidence positive response with real route/Answerability/Citation; confirm the original-file permissions, Readiness Gate, cache and counts/fingerprints. Check official provider authorization and privacy constraints before sending any real evidence.
8. On a provider failure, stop requests; restore `AI_CHAT_PROVIDER=njusehub` and the protected pre-change configuration, keep `AI_CHAT_MODEL=deepseek-v4-pro`, the school embedding settings, cache/Lua/resilience and worker state. Restart only Spring Boot. On code regression, restore the accepted merged JAR plus its backed-up config. Never restore a JAR without timeout/readiness protections.

No production operation, Git push or paid RAG benchmark is authorized by this document. Basic official completion compatibility is verified by the two minimal probes above. Real-evidence Answerability/Citation quality and end-to-end latency still require a separately approved production acceptance budget; no production performance claim follows from these minimal probes or stub success.
