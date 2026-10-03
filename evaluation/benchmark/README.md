# NJU Compass V1 vs V2 benchmark

This is evaluation-only tooling. The frozen 82-case `evaluation/test-cases.json` and existing `evaluation/smoke-cases.json` are inputs and are never rewritten. It does not change prompts, retrieval settings, data, production configuration, or the public API. `gold-labels.json` is a separate review sidecar; every generated label starts `NEEDS_REVIEW` (or `NOT_SCORABLE`) and none are auto-confirmed.

## Frozen comparison

- Baseline artifact/data: production commit `8c34ccdf16d283231ff972ca8860fff3baaa28a4`.
- V1: the same artifact and data, `app.rag.canonical-first-enabled=false`.
- V2: the same artifact and data, `app.rag.canonical-first-enabled=true`.
- Requests are serialized and paired. No model, prompt, embedding, TopK, threshold, or data setting is varied.
- The smoke sidecar selects five fixed cases plus the known mixed-query limitation (six total). Do not use this command as the full 82-case run.

## Phase A — save raw RAG results

For real in-process rankings and contexts, use the opt-in test runner below. It invokes the same `RagService`/`RetrievalService` code, does not start an HTTP server, and writes traces only under ignored `evaluation/results/`. It runs the six existing smoke cases sequentially once under V1 routing and once under V2 routing. Keep heap bounded and run it only on a trusted evaluation host with the intended database, Qdrant, and provider environment available:

```powershell
$env:MAVEN_OPTS = "-Xmx512m"
./mvnw -Dtest=BenchmarkTraceSmokeTest `
  -Dbenchmark.trace.enabled=true `
  -Dbenchmark.trace.output=evaluation/results/<unique-run-id> `
  -Dspring.jpa.hibernate.ddl-auto=validate `
  -Dapp.qdrant-backfill.enabled=false `
  -Dspring.sql.init.mode=never test
```

The opt-in runner aborts unless Hibernate is in `validate` mode and the Qdrant payload backfill is disabled. This prevents startup schema DDL and maintenance-runner writes during the trace run.

The internal package-private `askForEvaluation` entrypoint accepts a per-call V1/V2 selector; the configured feature flag is never changed. Normal `ask()` always follows the configured flag. The trace includes candidate content and actual retrieved/approved contexts, so keep the output private and never commit it.

After the test, score saved traces (no new RAG calls):

```powershell
python evaluation/benchmark/judge_ragas.py evaluation/results/<unique-run-id>
python evaluation/benchmark/score_trace_smoke.py evaluation/results/<unique-run-id>
```

`Expected Fact Recall` is a conservative, whitespace-normalized literal coverage proxy. It is custom and is not RAGAS Context Recall. RAGAS Faithfulness receives `approvedContexts`; RAGAS Context Recall receives actual `retrievedContexts` plus the existing frozen `expectedFacts` reference.

The old HTTP runner below remains useful for endpoint-level latency/citation smoke, but cannot produce true ranking/context metrics by itself.

Run `run_smoke.py` against two already-running, isolated base URLs:

```powershell
python evaluation/benchmark/run_smoke.py `
  --v1-url http://127.0.0.1:18081 `
  --v2-url http://127.0.0.1:8080
```

If query-free `RAG retrieval:` log extracts are captured after the serialized run, pass them with `--routing-diagnostics <path>`. The sidecar must contain ordered `V1` and `V2` arrays, each entry pairing the corresponding `caseId` with `retrievalLayer`, `canonicalCandidateCount`, `evidenceCandidateCount`, `fallbackTriggered`, and `fallbackReason`. This can recover route/fallback rates without exposing queries; it cannot recover ranked document IDs or generation context.

The V1 listener should be a temporary loopback-only process of the exact frozen JAR with only the feature flag overridden; it must not replace or restart the production service. Apply a finite heap/memory limit and stop it after the six paired requests. The runner is intentionally fail-closed when a case or gold sidecar entry is missing.

Each run writes `raw-v1.jsonl`, `raw-v2.jsonl`, `comparison.csv`, retrieval/generation summaries, `summary.json`, `summary.md`, and `failures.md` beneath `evaluation/results/<UTC timestamp>/`. Raw results are ignored by Git because answers may include full source text.

## HTTP smoke observation limits

The public RAG response still provides no true retrieval TopK or internal contexts. Final citation IDs must not be treated as ranked retrieval results. Use the in-process runner for trace metrics; the HTTP runner's Hit@3/MRR and context metrics remain N/A. No evaluation trace endpoint or production INFO logging is added. Citation Accuracy is computed only when an expected Evidence Document ID is verified; refusal accuracy is deterministic for the negative control. HTTP latency is end-to-end API latency, not a production SLA.

The frozen dataset has expected facts but no complete reference answers or expected-document-ID labels. `python evaluation/benchmark/build_gold_review.py` generates a four-batch human review pack, candidate Evidence metadata derived only from historical citation metadata, and a dynamic `gold-coverage.json`. Historical Document IDs above the original 1–20 Evidence ID range are withheld because they may overlap Canonical IDs; candidate title/sourceType still need current DocumentRole and source verification. Generated suggestions never enter scorable Gold fields.

Review `gold-review.md` in batches. Fill only its `Human Decision` fields; use `expectedFactsStatus` = `SUPPORTED`, `NEEDS_FIX`, or `AMBIGUOUS`. Confirmed `answerable=true` requires at least one verified Evidence Document ID; `answerable=false` requires `expectedDocumentIds: []`. Then run `python evaluation/benchmark/sync_gold_review.py`. It makes no provider calls and only applies sections explicitly marked `reviewStatus: CONFIRMED`. Re-run `build_gold_review.py` to refresh coverage after edits; it preserves existing human-confirmed values. Do not add or rewrite `evaluation/test-cases.json` as part of this review workflow.

The reports distinguish the evaluated Production Logic Version (`8c34ccdf16d283231ff972ca8860fff3baaa28a4`) from the Benchmark Tooling Version (`ae69fae6958fdfc5041677ea4f073d55fce45241`). The latter adds evaluation-only tracing and is not a new RAG logic version.

## Phase B — judge saved answers only

Ragas is pinned to 0.4.3, using the collections metric API (`.ascore(**kwargs)`). Create a temporary environment from `requirements-ragas.txt` and expose the same OpenAI-compatible provider settings as the application as process environment variables (`AI_BASE_URL`, `AI_API_KEY`, `AI_CHAT_MODEL`, and `AI_EMBEDDING_MODEL`). Optionally set `RAGAS_JUDGE_MODEL` and `RAGAS_JUDGE_EMBEDDING_MODEL`; use identical values for both versions. No answer calls are repeated:

```powershell
python evaluation/benchmark/judge_ragas.py evaluation/results/<timestamp>
```

Answer Relevancy can be scored from question/answer with the configured judge. Faithfulness and Context Recall are skipped unless actual retrieved contexts are present in Phase A raw rows; expected facts are never passed off as retrieved context. A judge/provider failure writes no replacement RAG data.

## Deterministic unit tests

```powershell
python -m unittest discover -s evaluation/benchmark/tests -v
```

These tests do not call any model or provider.
