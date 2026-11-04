# MILESTONES — market-brief

Learning-oriented implementation plan for [`docs/PRD.md`](docs/PRD.md), following
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Each milestone is small enough to build,
test, and understand in one sitting. Work them in order; M9 (spike) may run in
parallel any time after M4.

Ground rules for every milestone:

- Respect the dependency direction (ARCHITECTURE.md §1): application depends on
  domain; adapters depend on domain and the shared report format; HTTP depends on
  application and report format. The composition root wires application and
  adapters.
- Domain stays pure Kotlin — no framework/serialization imports.
- Every milestone ends with passing tests (`./gradlew test`).

---

## M0 — Skeleton + `/health` ✅

Gradle Kotlin DSL build, Ktor Netty server, `GET /health`, `testApplication`
test. **Done.**

- *Learned*: Gradle Kotlin DSL, Ktor routing, `testApplication` (in-memory
  server tests).

## M1 — Domain model ✅

The pure core: types + ports, nothing else.

- Implement `NewsItem` (with `type`, `region`, `subjects`, `assertion`,
  `direction`, `cause`, `metrics`, mandatory `source`), `Article`, `Snapshot`,
  `Report`, and value objects (`Instrument`, `DayWindow`, …) per GLOSSARY.md.
- Enforce the citation invariant **at construction** (ARCHITECTURE.md §2.3):
  `NewsItem` cannot be created without a `source`. Write a test proving the
  compiler/runtime rejects an uncited item.
- Declare the five port interfaces (`MarketDataFetcher`, `NewsSource`,
  `NewsItemExtractor`, `ReportStore`, `EvalStore`) — signatures only, no impls.

**Done when**: domain compiles with zero non-stdlib imports; invariant tests
pass.
**Learn**: Kotlin data classes, enums, value classes; parse-don't-validate;
what a port (interface) buys you.

## M2 — The pipeline with fakes ✅

`GenerateDailyReport` end-to-end, driven entirely by in-memory fakes.

- Implement `GenerateDailyReport` (ARCHITECTURE.md §3): snapshot → fetch →
  extract → assemble sections → save. One public method: `generate()`.
- Implement **fakes in test code**: `FakeMarketDataFetcher`, `StaticCorpusNewsSource`
  (a few hard-coded articles), `DeterministicNewsItemExtractor` (returns fixed
  items per article id), `InMemoryReportStore`.
- Section assembly (ARCHITECTURE.md §2.4): regional grouping, company/sector
  balance, summary rules — as domain services, unit-tested directly.

**Done when**: a test runs the full pipeline with fakes and asserts the
assembled `Report` structure (four sections, grouping rules hold).
**Learn**: constructor DI, use-case orchestration, testing through ports
without mocks frameworks, pure domain services.

## M3 — Market data adapter ✅

First real adapter: `MarketDataFetcher` over HTTP.

- `YahooQuoteFetcher` + composite with per-instrument-type fallbacks
  (ADR-0001), sources queried concurrently (coroutines).
- Map the full instrument set (PRD §7).
- Test against recorded JSON fixtures, not the live API.

**Done when**: `Snapshot` for all PRD §7 instruments from fixture-backed
tests; fallback kicks in when the primary fixture is absent.
**Learn**: Ktor client / HTTP, kotlinx.serialization, coroutines
(`async`/`await`), composite adapter pattern.

## M4 — LangChain4j extractor ✅

The LLM seam, behind `NewsItemExtractor`.

- `LangChain4jNewsItemExtractor`: single OpenAI-compatible chat model
  (`baseUrl`/`model` from YAML, `apiKey` from the environment variable named
  in the YAML, ADR-0003), JSON-object output → `NewsItem` candidates.
- The internal mapping seam (ARCHITECTURE.md §4): candidates failing the
  citation invariant are dropped and counted; only valid `NewsItem`s cross
  the port.
- Test with a stubbed HTTP server returning canned structured output — no
  live LLM in unit tests.
- The live check is an opt-in `liveTest` (tagged `live`, excluded from
  `build`); it asserts only citation structure, never model wording.
- **Extraction throughput** (prune review Q-3): `GenerateDailyReport` now
  extracts concurrently via the LangChain4j async chat API under a bounded
  fan-out (default 4 in flight), and preserves article order before assembly.
  Decision recorded in ADR-0005.

**Done when**: fixture-backed tests cover valid extraction, dropped
uncited candidates, and drop counts; one manual smoke run against a real
provider (key from env). Verified against the configured provider: the smoke
run returned 3 cited items and dropped 0 candidates.
**Learn**: LangChain4j chat models + structured output, provider-swappable
config, testing LLM adapters without an LLM.

## M5 — Presentation + persistence ✅

Expose the pipeline over HTTP and persist reports. **Done.**

- `JsonReportStore` (JSON snapshots to disk; round-trip test).
- HTTP DTOs + mapping (never serialize domain directly, ARCHITECTURE.md §5).
  The serializable report shape lives in `com.marketbrief.reportformat` and is
  shared with the store, so the persisted file and the HTTP response match.
- **Snapshot rendering**: group quotes by asset class in the JSON/HTML layer
  (`Snapshot` stays a flat list; each quote carries `instrument.assetClass`).
  `InstrumentQuote` carries the absolute day change, so fixed income renders
  `4.995 (+0.9 bp, +0.18%)`; other asset classes render value plus percent.
- Endpoints: `POST /report/generate`, `GET /report/{date}`,
  `GET /report/{date}.html` (kotlinx.html rendering).
- `testApplication` tests for each endpoint, wired with M2 fakes.
- **Snapshot completeness policy** (prune review Q-4): resolved in ADR-0006 —
  missing instruments travel in `Snapshot.missing` and render as
  "data unavailable"; `CompositeMarketDataFetcher` and `ReportAssembler` cover
  it with tests.

**Done when**: tests generate a report through HTTP and read it back as JSON
and HTML; `./gradlew run` serves a real generated report (fakes + M3/M4
adapters wired in `main`).
**Learn**: Ktor content negotiation, kotlinx.html DSL, DTO mapping, the
composition root doing real wiring.

## M6 — Scheduling ✅

- Scheduler triggers `GenerateDailyReport` daily at 09:00 Europe/Berlin,
  CET/CEST aware (wired in the composition root, not the use case).
- Keep the on-demand `POST /report/generate` for the PoC.

**Done when**: a test (injectable clock) proves the trigger fires at 09:00
Berlin across a DST boundary. **Done** — `DailyReportScheduler` has virtual-time
tests across both the spring and autumn transitions; a failed run is logged and
does not stop the schedule.
**Learn**: scheduled jobs, injecting a `Clock` for testability, timezone
handling.

## M7 — Eval harness (in progress)

Measure extraction quality (PRD §11, ARCHITECTURE.md §6).

- **Implemented**: eight gold files with expected `NewsItem`s; `RunEval`,
  `JsonEvalStore`, and `POST /eval/run` / `GET /eval/{id}` are wired.
- **Remaining**: add eval tests; include expected snapshot facts; validate metric
  calculations; measure cost instead of returning `0.0`; strengthen citation
  evaluation beyond checking quote presence; add an `EvalStore` test fake; run
  the gold cases against their packaged corpus articles. The production
  RSS/GDELT article IDs do not match the gold IDs, so the current eval endpoint
  can score an empty set and report perfect ratios.

**Done when**: tests prove known metrics for deterministic extraction, a planted
quote absent from the article fails verification, and the documented eval
metrics are implemented rather than placeholders.
**Learn**: precision/recall metrics, eval design, "don't mark your own
homework" verification.

## M8 — Live news ingestion (in progress)

Use free live sources in production and select regional items by the defined
policy.

- **Implemented**: `RssNewsSource` and `GdeltNewsSource` are wired through
  `CompositeNewsSource`, which deduplicates URLs and sorts by publication date.
- **Remaining**: fixture-backed feed tests and live smoke run; regional ranking
  by publication date, per-outlet and per-region caps, event-key deduplication,
  and a corpus test for stable selection and `GLOBAL` exclusion. Feed failures
  currently become empty results and need an explicit operational policy.

**Done when**: feed fixtures and live smoke verification pass, and report
selection applies the documented regional policy with tests.
**Learn**: RSS/XML parsing, HTTP resilience (timeouts, retries), multi-source
dedup.

## M9 — LangChain4j spike (planned, parallel, time-boxed)

PRD §12: probe structured output with the `NewsItem` shape across providers
(DeepSeek / Kimi / open models). Exit criteria: minimal reproducer +
observed-vs-expected + upstream issue/PR link, **or** documented "no issue
found".

**Done when**: findings documented (e.g. `docs/spike-langchain4j.md`) per the
exit criteria.
**Learn**: structured-output edge cases (enums, optionals, nested citation
objects), provider divergence.

---

## Release mapping (PRD §13)

These are release targets, not completion status; M7 and M8 remain in progress.

- PoC = M0–M5 · Eval = M7 · Live = M6 + M8 · Spike = M9
