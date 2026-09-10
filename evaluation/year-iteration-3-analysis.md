# Year / Cohort Iteration 3 - Read-only Analysis

## 1. Conclusion

Fifteen baseline failures have a confirmed Year/cohort component. They are not one homogeneous problem:

- Transfer cycle versus cohort: TRAG-015, TRAG-016, TRAG-018, TRAG-019.
- Historical cohort facts: TRAG-031, TRAG-041, TRAG-049.
- Explicit applicant stage: TRAG-028, TRAG-072.
- Cohort mentioned in ordinary guide content but absent from metadata: TRAG-034, TRAG-036, TRAG-048.
- Query gives a transfer cycle but omits the cohort/stage needed to select the expected row: TRAG-023, TRAG-025, TRAG-027.

TRAG-020, TRAG-024, and TRAG-029 were rechecked as suspected cases and are not primarily Year failures: their correct or sufficient evidence remains visible under the current year filter. Their failures belong to retrieval/evidence coverage or Answerability.

TRAG-072 now passes after Entity Resolution 2B by retrieving the 2026 personal guide. It remains a required Year regression target because its baseline failure exposed the first-year/second-year row-selection defect in the official table.

## 2. What the official 2026 table actually means

The original PDF title identifies the document/transfer-cycle version as 2026. Its table header is `学院名称 | 年级 | 专业名称 | 接收计划名额 | ...`. The values `2025` and `2024` are explicitly under the **年级** column, not a policy publication-year column.

For the 2026 transfer cycle:

- cohort 2025 is the first-year applicant row;
- cohort 2024 is the second-year applicant row.

The current `StructuredPolicyChunker` reads that column into a field named `policyYear`. `VectorIndexService` then computes `effectiveYear = policyYear != null ? policyYear : Document.year`. As a result, a question containing “2026年” filters out well-structured rows whose payload correctly contains the table value 2025 or 2024, even though those rows belong to the 2026 document and transfer cycle.

Some baseline PASS cases work only because parsing failed closed on row metadata: mixed chunks such as c121/c122 and c146 have `policyYear=null`, so their `effectiveYear` falls back to the document value 2026. This is accidental visibility, not correct temporal modeling.

## 3. Detailed failure audit

The filter notation below abbreviates the existing department expression as `dept(...)`. Every query is single-year (`multiYearQuery=false`).

| Case | Query year semantics | Ground-truth document / chunk | Current metadata | Body meaning / stage | Current final filter | Why the correct evidence is lost or confused |
|---|---|---|---|---|---|---|
| TRAG-015 | transferCycleYear=2026 | doc6/c116 | Document.year=2026; policyYear=2025; effectiveYear=2025 | cohort 2025; first-year rule | `dept(文学院) AND effectiveYear=2026` | c116 is excluded solely because cohort 2025 was treated as effective policy year. |
| TRAG-016 | transferCycleYear=2026 | doc6/c116 | 2026 / 2025 / 2025 | cohort 2025; first-year eligibility | `dept(文学院) AND effectiveYear=2026` | Same exclusion as TRAG-015. |
| TRAG-018 | transferCycleYear=2026 | doc6/c125 | 2026 / 2025 / 2025 | cohort 2025; only math row in the table | `effectiveYear=2026` | The complete structured math chunk is excluded. Earlier “chunk boundary” attribution was incorrect. |
| TRAG-019 | transferCycleYear=2026 | doc6/c125 | 2026 / 2025 / 2025 | cohort 2025; competition bonus method | `effectiveYear=2026` | The method is present in c125 but c125 is excluded. |
| TRAG-023 | transferCycleYear=2026; cohort/stage omitted | doc6/c143 | 2026 / 2024 / 2024 | Ground Truth expects cohort 2024 / second-year “2 of 4” rule | `dept(软件学院) AND effectiveYear=2026` | Both cohort rows are excluded; moreover the question does not state why the cohort-2024 row should be selected. |
| TRAG-025 | transferCycleYear=2026; cohort/stage omitted | doc6/c140 | 2026 / 2024 / 2024 | Ground Truth expects cohort 2024 / second-year two-course rule | `dept(计算机学院) AND effectiveYear=2026` | c140 is excluded. The cohort-2025 rule is materially different, so a safe system cannot infer the intended row from 2026 alone. |
| TRAG-027 | transferCycleYear=2026; cohort/stage omitted | doc6/c143 | 2026 / 2024 / 2024 | Ground Truth expects cohort 2024 / second-year math plus “2 of 4” | `effectiveYear=2026` | Official c143 is excluded, leaving a personal source; the question itself is cohort-underspecified. |
| TRAG-028 | transferCycleYear=2026 plus FIRST_YEAR | doc6/c121 (correct); c122 contains old row | c121/c122: Document.year=2026; policyYear=null; effectiveYear=2026 | correct row is cohort 2025 / first-year; wrong row is cohort 2024 / second-year | `effectiveYear=2026` | Both rows remain visible because metadata extraction failed; c122 mixes the 2024 social row with other records and ranks above c121. No stage/cohort filter is possible. |
| TRAG-031 | cohortYear=2023 historical distribution | doc28/c313 | Document.year=2026; policyYear=null; effectiveYear=2026 | document authored 2024-07-25; fact describes cohort 2023 | `effectiveYear=2023` | c313 is completely excluded. Changing Document.year to 2023 would be semantically wrong. |
| TRAG-034 | cohortYear=2025 applicability | doc19/c165 | Document.year=2026; policyYear=null; effectiveYear=2026 | “自2025级起”; cohort-scoped guide statement | `effectiveYear=2025` | The body cohort is not extracted; target document disappears. |
| TRAG-036 | cohortYear=2025 applicability | doc19/c166 | Document.year=2026; policyYear=null; effectiveYear=2026 | 2025-cohort ranking arrangement | `effectiveYear=2025` | Same missing body-cohort metadata. |
| TRAG-041 | cohortYear=2023 historical distribution | doc29/c322 | Document.year=2026; policyYear=null; effectiveYear=2026 | fact explicitly says “2023级分流情况” | `effectiveYear=2023` | c322 is completely excluded. |
| TRAG-048 | cohortYear=2025 guide scope | doc25/c305 | Document.year=2026; policyYear=null; effectiveYear=2026 | title/body identify 2025 cohort | `effectiveYear=2025` | Target guide is excluded by its import-time document year. |
| TRAG-049 | cohortYear=2024 historical plan | doc25/c305 | Document.year=2026; policyYear=null; effectiveYear=2026 | 2024-cohort plan and counts inside a 2025-cohort guide | `effectiveYear=2024` | c305 is excluded even though it contains the exact fact. |
| TRAG-072 | transferCycleYear=2026 plus FIRST_YEAR | official doc6/c133; personal doc14/c94 | c133: year=2026, policyYear=2025, effectiveYear=2025; c94: effectiveYear=2026 | cohort 2025 / first-year six-course rule | `dept(电子科学与工程学院) AND effectiveYear=2026` | At baseline, mixed c136 exposed the cohort-2024 rule and caused YEAR_MISMATCH. After 2B, personal c94 makes the case pass, but official c133 remains wrongly excluded. |

## 4. StructuredPolicyChunker diagnosis

The chunker successfully creates clean row-level chunks for several records (c116, c125, c133, c139/c140, c142/c143). However:

1. `RecordHeader.policyYear` is populated from the PDF column named **年级**. Its actual meaning is `cohortYear`.
2. It has no input for document/transfer-cycle year, so it cannot emit both cycle and cohort.
3. When `hasReliableMetadata` sees another year row or department, it sets department/major/year metadata to null but does not repair the record boundary. This produces mixed chunks such as c121/c122 and c135/c136.
4. A mixed chunk falling back to `Document.year=2026` can outrank the correct row and select the wrong applicant stage.
5. Generic guide chunks do not pass through this table-specific extractor, so explicit body phrases such as `2023级` and `自2025级起` are not represented at all.

Year V2 therefore does require a focused StructuredPolicyChunker change, but that change alone is insufficient. A lightweight generic cohort annotation step is also needed for ordinary guide chunks.

## 5. Temporal concepts actually required

### Keep

- `Document.year`, with a strict definition of **document publication/version year**. Existing imported values for docs 19, 25, 28, and 29 are 2026 even though their Ground Truth source/version evidence points to 2025 or 2024; this is data quality to validate during a later migration.
- `Chunk.policyYear`, but define it as **policy/transfer-cycle applicable year**, not the student cohort. For the official table, it should be 2026 for both the 2025 and 2024 rows.
- `effectiveYear` temporarily for backward compatibility only. It must stop being the sole temporal truth.

### Add now

- `Chunk.cohortYear` (nullable scalar): the `XXXX级` to which a row or fact applies. This single field explains every historical-year target in the current evaluation set.

### Do not add yet

- `factYear`: the current failures say `2023级` or `2024级`; they are cohort facts, not independent calendar-event dates. No evaluated case requires a separate fact-year field yet.
- persisted `applicantStage`: for the official cycle, stage is deterministically derived from `(policyYear - cohortYear)` and from explicit query words. Persisting another field would duplicate the same information. Keep `FIRST_YEAR/SECOND_YEAR` in the resolved query and derive the target cohort. Reconsider persistence only if a real source uses stage without a resolvable cohort.

## 6. Minimal Year Model V2 design

### A. Field semantics

- `Document.year`: publication/version year.
- `Chunk.policyYear`: policy or transfer-cycle year.
- `Chunk.cohortYear`: student cohort/applicable grade year.
- `effectiveYear`: legacy compatibility field, never used as the only hard-filter dimension once new metadata is present.
- Resolved query temporal intent: `queryYear`, `yearRole` (`POLICY_CYCLE`, `COHORT`, `MULTI_YEAR`, `UNKNOWN`), and optional `applicantStage`.

### B. Field production

- Document import supplies/validates document version year.
- Structured official tables take `cohortYear` from the `年级` column and `policyYear` from the document/cycle context (the 2026 title/version).
- Generic chunk annotation recognizes explicit high-confidence cohort forms such as `2023级`, `自2025级起`, and `2024级分流`. A chunk with conflicting/multiple cohorts should not receive a scalar hard-filter value.

### C. Storage

- Store `cohortYear` in MySQL Chunk and mirror it in Qdrant payload.
- Keep document year in MySQL Document and Qdrant `year`.
- Keep corrected policy cycle in MySQL Chunk and Qdrant `policyYear`.
- Keep applicant stage query-side/derived initially; do not add a database field in the minimum version.

### D. Query parsing

- `2026年...转专业/准入/政策` => `yearRole=POLICY_CYCLE`, `policyYear=2026`.
- `2025级...` => `yearRole=COHORT`, `cohortYear=2025`.
- `2026年大一...` => `policyYear=2026`, `applicantStage=FIRST_YEAR`, derive `cohortYear=2025`.
- `2026年大二...` => derive `cohortYear=2024`.
- `2023级分流情况` => `cohortYear=2023`, regardless of document year.
- Multiple explicit years retain multi-year behavior and do not collapse to one scalar.
- No-explicit-year experience queries preserve the existing cross-year strategy.

### E. Retrieval filtering

- Policy query: hard-filter `policyYear` to the cycle; add `cohortYear` only when the query states cohort/stage or the entity has only one applicable cohort row.
- Cohort/historical query: hard-filter `cohortYear`, not document/effective year.
- Policy cycle with multiple cohort rows and no stage: retrieve all matching cohort rows within that cycle and preserve their cohort labels. Do not guess one row.
- Never replace this with unrestricted cross-year policy search.

### F. Fallback when temporal metadata is missing

- For high-risk policy queries, first use exact new metadata. If unavailable, use the legacy `effectiveYear` path as a separately marked fallback, not as an OR branch mixed into exact results.
- For explicit cohort historical facts with missing `cohortYear`, allow a bounded second pass without the legacy year filter and require the exact cohort token (for example `2023级`) in candidate content before it can be used.
- If a policy query is stage-ambiguous and two rows conflict, fail closed or ask for cohort/stage rather than selecting the newest vector hit.

### G. Old-data compatibility

- Add new fields as nullable.
- Reindex structured official tables by interpreting existing row `policyYear` values as cohort years only for known table layouts; set corrected policy cycle from the document version.
- Leave legacy points searchable until their document is reprocessed; record `temporalMetadataVersion` or equivalent migration state if needed for diagnostics.
- Do not bulk-copy `effectiveYear` into `cohortYear`; that would reproduce the current semantic error.

## 7. Primary solution classification

The categories below are mutually exclusive by primary cause; several cases also have secondary issues.

| Class | Count | Cases | Expected solution |
|---|---:|---|---|
| A. transfer cycle ↔ cohort | 4 | TRAG-015, 016, 018, 019 | Separate policy cycle 2026 from cohort 2025 and query the correct dimension. |
| B. historical cohort fact | 3 | TRAG-031, 041, 049 | Extract/index `cohortYear` from body facts; filter by cohort, not document year. |
| C. applicant stage | 2 | TRAG-028, 072 | Parse 大一/大二, derive cohort from cycle, and ensure one row per chunk. |
| D. chunk metadata extraction | 3 | TRAG-034, 036, 048 | Annotate high-confidence cohort expressions in generic guide chunks. |
| E. query year semantics | 3 | TRAG-023, 025, 027 | Treat 2026 as cycle year; because stage is omitted, retrieve both labeled rows or request clarification rather than guessing the Ground Truth cohort. |
| F. actually not Year | 3 | TRAG-020, 024, 029 | Leave for retrieval/evidence coverage or Answerability; correct evidence is not excluded by current year filter. |

## 8. Regression set

`evaluation/year-regression-cases.json` contains 24 unchanged Ground Truth cases:

- 15 confirmed target failures;
- 9 baseline PASS guards;
- explicit policy-cycle, explicit cohort, historical cohort facts, first/second year, multi-year experience, and no-explicit-year policy/experience coverage.

Each case adds only `selectionReason`, `yearSemanticRole`, `baselineClassification`, and `regressionRole`. The original 82-case dataset and its Ground Truth were not modified.

## 9. Expected next-iteration impact

Directly addressable with the minimum model and reindex: TRAG-015, 016, 018, 019, 028, 031, 034, 036, 041, 048, 049, and the official-source path for TRAG-072.

TRAG-023, 025, and 027 cannot safely be forced to their current expected cohort because the questions specify cycle 2026 but omit stage/cohort while the official table contains conflicting 2025 and 2024 rows. Year V2 should make the ambiguity explicit; the evaluation questions may later need a cohort/stage qualifier if deterministic PASS is required.
