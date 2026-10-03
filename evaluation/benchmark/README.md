# NJU Compass V1 vs V2 benchmark

This is evaluation-only tooling. It does not change application code, prompts, retrieval settings, data, or production configuration. The frozen 82-case `evaluation/test-cases.json` and existing `evaluation/smoke-cases.json` are inputs and are never rewritten. `smoke-gold.json` is a small, separately reviewable metadata sidecar; absent or uncertain gold values remain null.

## Frozen comparison

- Baseline artifact/data: production commit `8c34ccdf16d283231ff972ca8860fff3baaa28a4`.
- V1: the same artifact and data, `app.rag.canonical-first-enabled=false`.
- V2: the same artifact and data, `app.rag.canonical-first-enabled=true`.
- Requests are serialized and paired. No model, prompt, embedding, TopK, threshold, or data setting is varied.
- The smoke sidecar selects five fixed cases plus the known mixed-query limitation (six total). Do not use this command as the full 82-case run.

## Phase A — save raw RAG results

Run `run_smoke.py` against two already-running, isolated base URLs:

```powershell
python evaluation/benchmark/run_smoke.py `
  --v1-url http://127.0.0.1:18081 `
  --v2-url http://127.0.0.1:8080
```

If query-free `RAG retrieval:` log extracts are captured after the serialized run, pass them with `--routing-diagnostics <path>`. The sidecar must contain ordered `V1` and `V2` arrays, each entry pairing the corresponding `caseId` with `retrievalLayer`, `canonicalCandidateCount`, `evidenceCandidateCount`, `fallbackTriggered`, and `fallbackReason`. This can recover route/fallback rates without exposing queries; it cannot recover ranked document IDs or generation context.

The V1 listener should be a temporary loopback-only process of the exact frozen JAR with only the feature flag overridden; it must not replace or restart the production service. Apply a finite heap/memory limit and stop it after the six paired requests. The runner is intentionally fail-closed when a case or gold sidecar entry is missing.

Each run writes `raw-v1.jsonl`, `raw-v2.jsonl`, `comparison.csv`, retrieval/generation summaries, `summary.json`, `summary.md`, and `failures.md` beneath `evaluation/results/<UTC timestamp>/`. Raw results are ignored by Git because answers may include full source text.

## Observation limits

The public RAG response provides final answers and approved Evidence citation metadata, but not the true retrieval TopK, canonical candidate IDs, fallback diagnostics, or exact generation/Answerability context. Final citation IDs must not be treated as ranked retrieval results. Consequently Hit@3, MRR, Faithfulness, and Context Recall are N/A in the real smoke output until the application has a non-invasive evaluation observation channel. The code does not add such an endpoint or alter production behavior. Citation Accuracy is computed only when an expected Evidence Document ID is verified; refusal accuracy is deterministic for the negative control. Latency is end-to-end API latency, not a production SLA.

The current frozen dataset has expected facts but no complete reference answers or expected-document-ID labels. Sidecar IDs are populated only where the production mapping is known; `manualReviewRequired` remains true for incomplete/unverified gold.

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
