# Architecture — market-brief

`market-brief` is a Kotlin service that produces a **daily summary of the most
important global investment-market news** for the previous day. It gathers free
news and market data, extracts the meaningful information into typed units
(`NewsItem`s), and assembles a templated report (JSON + HTML).

This document is the source of truth for the implemented architecture, including
its rationale and current limitations. It follows a
**Domain-Driven Design (DDD)** style with single-direction dependencies between
layers. It is the code-level counterpart to [`PRD.md`](PRD.md) (the *what*) and
[`GLOSSARY.md`](GLOSSARY.md) (the *vocabulary*).

---

## 1. Layer overview

Dependencies point **inward**. The domain knows nothing about HTTP, JSON, RSS,
or LLM providers.

```
┌──────────────────────────────────────────────────────────────┐
│                     presentation / interface                  │
│   Ktor routes, HTML rendering (kotlinx.html), HTTP DTOs       │
└──────────────────────────────┬───────────────────────────────┘
                               │ calls
┌──────────────────────────────▼───────────────────────────────┐
│                        application                            │
│   GenerateDailyReport (use case), RunEval (use case)          │
└──────────────────────────────┬───────────────────────────────┘
                               │ uses (never imports adapters)
┌──────────────────────────────▼───────────────────────────────┐
│            domain                                             │
│  NewsItem, Report, Snapshot, value objects, ports (seams)     │
└──────────────────────────────▲───────────────────────────────┘
                               │ implements
┌──────────────────────────────┴───────────────────────────────┐
│                        adapters                               │
│  Yahoo/fallback market data, RSS/GDELT news ingestion,        │
│  LangChain4j extractor, JSON report/eval store                │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                     composition root                          │
│  `CompositionRoot`: constructs and wires dependencies; `main` │
│  starts the scheduler + Ktor. The ONLY wiring object that     │
│  imports both application and adapters.                       │
└──────────────────────────────────────────────────────────────┘
```

There is **no edge between application and adapters**. Use cases depend
on domain ports; adapters implement them; the composition root connects the
two. That is what keeps the dependency graph acyclic.

Runtime configuration also belongs to the composition root: non-sensitive
settings come from `config/application.yml` (path overridable with
`MARKET_BRIEF_CONFIG`), while the LLM API key is injected as an environment
variable by 1Password's `op run` — the variable is named in the YAML. Adapters
never read the file or the environment themselves; they receive plain config
objects.

| Layer          | Package                      | Depends on                |
|----------------|------------------------------|---------------------------|
| domain         | `com.marketbrief.domain`     | nothing (pure Kotlin)     |
| application | `com.marketbrief.application` | domain + coroutines |
| adapters | `com.marketbrief.adapters` | domain + report format + third-party libraries |
| report format | `com.marketbrief.reportformat` | domain (shared serializable report shape) |
| presentation | `com.marketbrief.http` | application + report format |
| scheduling | `com.marketbrief.scheduling` | coroutines + `java.time` + SLF4J (no domain/app imports) |
| configuration | `com.marketbrief.config` | kaml + kotlinx.serialization |
| composition root | `com.marketbrief` | application + adapters + domain + http + scheduling + configuration + Ktor client |

---

## 2. Domain layer (`domain/`)

Pure Kotlin, **no framework or serialization annotations**. The domain owns the
vocabulary in `GLOSSARY.md`; adapters own any external representation.

### 2.1 Core types

**`NewsItem`** is the central domain type — the typed unit extracted from an
article:

- `type` ∈ { `MARKET_MOVE`, `COMPANY`, `SECTOR`, `MACRO` }
- `region` ∈ { US, CHINA, EUROPE, LATAM, ASIA, GLOBAL } — a *tag*, not a type
- `subjects` — the companies/tickers, sectors, indices, or asset classes the
  item is about
- `assertion` — the claim, a single natural-language sentence
- `direction` — optional UP / DOWN / FLAT; typically set on moves
- `cause` — optional free text on `MARKET_MOVE` items; must itself be
  source-backed
- `metrics` — extracted numbers with units (e.g. "−1.2%", "$42B")
- `source` — one article id plus one or more quote spans intended to support the
  item (**mandatory**; see ADR-0002). Construction checks that the citation and
  its quote spans are non-empty; it does not verify the quotes against article
  text.

**`Snapshot`** is the structured market-numbers data: a flat list of
`InstrumentQuote`s, each tagged with an `Instrument` and its asset class
(equities, fixed income, FX & volatility, commodities, crypto), plus the set of
requested `Instrument`s that could not be resolved (`missing`). The report
groups quotes by asset class when rendered. Its numbers come from a market-data
API — **never** from article prose (ADR-0001). Missing instruments stay
explicit so absence is never rendered as a market fact (ADR-0006).
`missing` defaults to `emptySet()` for callers that know the snapshot is
complete; a producer that skips a requested instrument must pass the gap
explicitly. The type cannot infer completeness because it does not retain the
requested set.

- **`Report`** is the assembled output document: the four sections built from a
  `Snapshot` plus the extracted `NewsItem`s. Each `NewsItem` appears once:
  `COMPANY` and `SECTOR` items go to the company/sector section, never the
  regional sections. The summary section also carries `otherNews` for GLOBAL
  `MACRO` and `MARKET_MOVE` items that belong to no continent.

**`Article`** is the input aggregate: headline, publisher-page text in `body`,
`outlet`, URL, and publication date. A headline or RSS teaser alone is not an
eligible live `Article`.

### 2.2 Two kinds of numbers

Kept strictly separate:

| Kind            | Origin          | Citation required? |
|-----------------|-----------------|--------------------|
| Snapshot values | market-data API | no (authoritative) |
| `NewsItem.metrics` | prose extraction | yes (`source`)  |

### 2.3 The citation invariant (parse, don't validate)

ADR-0002's core rule — an assertion without a `source` is an *extraction
failure*, not a valid output — is enforced **at construction**: `NewsItem`
cannot be created without a `source`. Construction checks citation shape, not
whether its quote spans occur in the article or support the assertion.

The mapping from raw LLM structured output to `NewsItem` — where invalid
candidates are dropped and recorded — is an **internal seam of the extractor
adapter** (§4), not an application-layer step. Callers of
`NewsItemExtractor.extract(...)` receive only valid `NewsItem`s. This deepens
the extractor seam: one method to learn, no ordering constraint, and the
invariant holds for every current and future caller (pipeline, eval, tests).

### 2.4 Section assembly rules

Section assembly maps `NewsItem`s to the four report sections: regional grouping
(US / China / Europe / Latam / Asia), the
company/sector balance (US / China / Global, never US-only), and the summary.
The summary's S&P 500 and Nasdaq Composite levels and moves come from the
`Snapshot`; causes come from matching `MARKET_MOVE` items through the
domain-owned index aliases (`ReportInstruments.summaryInstrumentFor`). One
sentence per index. An index present in `Snapshot.missing` renders as
`"<index>: data unavailable."`; a complete snapshot with no index quotes
renders as "No major index moves." GLOBAL `MACRO` and `MARKET_MOVE` items are
carried as `Report.otherNews` (the summary's "other important news") rather
than dropped. A `COMPANY` or `SECTOR` item appears only in the company/sector
section; it is never duplicated into the regional sections. Regional items are
ranked by Article publication date (newest first), with outlet and Article ID
tie-breakers. Each region includes at most five items and at most two from one
outlet; duplicate events are removed using normalized type, region, and sorted
subjects. `GLOBAL` non-company/sector items are excluded from regional sections
and remain in `otherNews`. Company/sector items are balanced round-robin across
US, China, and a combined Global bucket, up to ten items.

### 2.5 Ports (interfaces owned by the domain)

The seams where adapters plug in. The domain *declares* them;
the adapters layer *implements* them.

```kotlin
interface MarketDataFetcher {                     // free market-data sources (ADR-0001)
    suspend fun snapshot(instruments: Set<Instrument>): Snapshot
}

interface NewsSource {                     // free news ingestion
    fun fetch(window: DayWindow): List<Article>
}

interface NewsItemExtractor {              // the LLM seam (LangChain4j)
    suspend fun extract(article: Article): NewsItemExtraction
}

interface ClaimSupportJudge {                // citation-support evaluation
    suspend fun judge(item: NewsItem): ClaimSupportJudgment
}

interface ReportStore {                    // persist / load reports
    fun save(report: Report)
    fun load(date: LocalDate): Report?
}

interface EvalStore {                      // persist / load eval reports
    fun save(report: EvalReport)
    fun load(id: String): EvalReport?
}
```

Current production and test implementations are listed below. Test fakes are
listed separately from production wiring:

- `MarketDataFetcher`: `CompositeMarketDataFetcher` (Yahoo primary, CNBC
  fixed-income fallback); `FakeMarketDataFetcher` in tests.
- `NewsSource`: report generation uses `RssNewsSource` and `GdeltNewsSource` in
  `CompositeNewsSource`; evaluation uses `JsonCorpusNewsSource` so Gold IDs
  resolve to their packaged Article text. Tests use static corpus fakes.
- `NewsItemExtractor`: `LangChain4jNewsItemExtractor`;
  `DeterministicNewsItemExtractor` in tests.
- `ClaimSupportJudge`: `LangChain4jClaimSupportJudge`; deterministic fake in tests.
- `ReportStore`: `JsonReportStore`; `InMemoryReportStore` in tests.
- `EvalStore`: `JsonEvalStore`; `InMemoryEvalStore` in tests.

`QuoteFetcher` is the adapters-internal seam: `YahooQuoteFetcher` and
`CnbcQuoteFetcher` implement it; only the composite implements the domain port.

`NewsItemExtractor` is the most important seam: the LangChain4j adapter
implements it, and the structured-output investigation targets it directly
(see §7).

---

## 3. Application layer (`application/`)

Use cases orchestrate the ports and domain services.

- **`GenerateDailyReport`** — the pipeline:
  1. `MarketDataFetcher.snapshot(...)` → `Snapshot`.
  2. `NewsSource.fetch(...)` → `Article`s from the previous calendar day
     (`DayWindow.forReportDate(date)`).
  3. `NewsItemExtractor.extract(...)` → valid `NewsItem`s (invalid candidates
     never cross the seam; see §2.3). Articles are extracted concurrently under
     a fixed permit count using the adapter's non-blocking chat call; `awaitAll`
     preserves article order before assembly (ADR-0005).
  4. Section assembly → `Report`.
  5. `ReportStore.save(...)`.
- **`RunEval`** — filters fetched articles to gold-set IDs, runs extraction,
  computes exact and field-level item metrics, checks quote presence, judges
  citation support, and saves a versioned eval report (see §6). Evaluation is
  sequential. The composition root supplies `JsonCorpusNewsSource` so eval uses
  the packaged Articles referenced by the Gold set, not the live RSS/GDELT
  source. `RunEval` fails if no Gold set Articles match or if any are missing.

Each use case keeps its orchestration behind one method (`generate()`, `run()`).
`GenerateDailyReport` is tested through in-memory port fakes.

`GenerateDailyReport.generate` is `suspend`: the `MarketDataFetcher` and
`NewsItemExtractor` ports are suspending, and the HTTP layer calls the use case
from its request coroutine. Concurrent calls for the same report date share one
in-flight run; the run is detached from the requesting coroutine, so a
disconnected client cannot cancel work shared by another caller.

A scheduler (wired in the composition root, not part of the use case) triggers
`GenerateDailyReport` at 09:00 Europe/Berlin (CET/CEST aware); the PoC also
exposes an on-demand trigger. `DailyReportScheduler` owns no business logic: it
takes the current instant from an injected `Clock`, sleeps until the next 09:00
in the Berlin zone, runs the supplied action, and repeats. A failed run is
logged and the schedule continues, so one provider outage does not silence the
next day's report.

---

## 4. Adapters layer (`adapters/`)

Concrete adapters implementing the domain ports.

- **`YahooQuoteFetcher` / `CnbcQuoteFetcher`** — implement the internal
  `QuoteFetcher` seam. `CompositeMarketDataFetcher` is the single
  `MarketDataFetcher` adapter: Yahoo is primary, per-asset-class fallbacks (CNBC
  for non-US yields) fill gaps, all sources are queried concurrently, and the
  primary wins when two sources cover the same instrument (ADR-0001). Requests
  are bounded at 15 seconds; CNBC decodes each quote independently so one
  malformed element does not discard the batch. The composite records
  unresolved instruments in `Snapshot.missing` and logs the gap.
- **`RssNewsSource` / `GdeltNewsSource`** — the live news sources wired through
  `CompositeNewsSource`. RSS uses BBC, Guardian, CNBC, and MercoPress feeds; GDELT uses
  its document API. Both fetch publisher-page text before constructing an
  `Article`. `PublisherArticleTextFetcher` selects known publisher body
  containers or `article` / `main` content and requires at least 80 words. It rejects non-HTTP URLs and
  `news.google.com` redirect URLs. A failed request or page without enough
  article text skips that candidate. The word-count floor filters headlines and
  short teasers; it does not prove that the full story was extracted. Feed
  failures fetching individual publisher pages are logged and skipped. RSS
  adapters tolerate failed feeds when at least one configured feed succeeds;
  `CompositeNewsSource` tolerates a failed adapter when another source succeeds.
  If all feeds or all sources fail, `NewsFeedUnavailableException` propagates and
  report generation fails instead of presenting an outage as an empty news day.
  A successful feed that contains no matching articles is a valid empty result.
  `NewsSource.fetch` is synchronous; these adapters use
  `runBlocking` for HTTP calls. `JsonCorpusNewsSource` remains available for
  eval and is not wired into production report generation.
- **`LangChain4jNewsItemExtractor`** — the LLM adapter. Uses a single
  **OpenAI-compatible** chat model so DeepSeek, Kimi, Ollama, OpenRouter, etc.
  are swappable (ADR-0003); `baseUrl` and `model` come from
  `config/application.yml`, the API key from 1Password via `op run`, and both
  reach the adapter as an `OpenAiCompatibleConfig`. It asks the model for
  structured JSON and maps it to typed `NewsItem`s. It returns token usage and
  an estimated cost when configured token rates are available. Requests use
  LangChain4j's `chatAsync`, so the port suspends instead of parking a thread.
  The adapter requests OpenAI JSON-object mode with the item shape described in
  its prompt and parses the answer with kotlinx.serialization — the wire
  contract stays explicit instead of leaning on provider-specific JSON-schema
  machinery. The citation's `articleId` is the input `Article`'s id, never
  something the model echoes back; the model supplies only the quote spans. The
  raw structured output → `NewsItem` mapping is the adapter's internal seam:
  candidates that fail the citation invariant (§2.3) or any other domain
  invariant are dropped and counted (`droppedCandidateCount`) here, so only
  valid `NewsItem`s cross the port. The port returns these items with token
  usage metadata for eval cost estimates. Provider and transport failures — including
  authentication, missing model, rate limiting, server errors, and unreachable
  endpoints — propagate so a broken configuration or outage fails the run
  loudly. Only content-level failures, such as empty or malformed model output,
  are isolated per article.
- **`LangChain4jClaimSupportJudge`** — checks whether the exact `Source` quote
  spans support a `NewsItem` assertion. It uses the configured OpenAI-compatible
  model and returns `SUPPORTED`, `UNSUPPORTED`, or `INDETERMINATE`. Missing quote
  spans are caught independently before calling the judge. The eval Markdown
  report includes a deterministic sample for manual review.
- **`JsonReportStore` / `JsonEvalStore`** — persist reports and eval results
  as JSON snapshots to disk (no DB in v1). The report store writes the shared
  wire representation (see §5), so the persisted file and the HTTP response are
  the same shape.

## 5. Presentation layer (`http/`)

Ktor server exposing a small JSON/HTML API. Domain objects are **not**
serialized directly; `com.marketbrief.reportformat` owns the `@Serializable` report
shape, and both the presentation layer and `JsonReportStore` map through it.

The snapshot stays a flat list in the domain. The wire mapper groups quotes by
`instrument.assetClass` for rendering and formats each quote once: fixed income
as `4.995 (+0.9 bp, +0.18%)`, other asset classes as value plus percent. Missing
instruments travel as `missing` inside their asset-class group and render as
"data unavailable" (ADR-0006), never as a market fact.

| Method | Path                       | Purpose                          |
|--------|----------------------------|----------------------------------|
| GET    | `/health`                  | Liveness                         |
| POST   | `/report/generate`         | Trigger `GenerateDailyReport`    |
| GET    | `/report/{date}`           | JSON report                      |
| GET    | `/report/{date}.html`      | Rendered HTML (kotlinx.html)     |
| POST   | `/eval/run`                | Trigger `RunEval`                |
| GET    | `/eval/{id}`               | Eval report                      |

`POST /report/generate` uses the current report date and the previous calendar
day's news window. An invalid retrieval date returns `400`; an absent stored
report returns `404` with a JSON error.

---

## 6. Evaluation pipeline

The current Gold set has expected `NewsItem`s for 32 packaged Articles.
Snapshot values are tested separately with recorded market-data fixtures; they
are not inputs to `RunEval`. `RunEval` computes exact item precision/recall,
field-level precision/recall, citation quote presence, semantic citation
support, missing-item rate, token usage, and estimated cost. Exact item matches
are one-to-one. Field-level matches use a maximum-weight one-to-one assignment
among pairs with at least one shared subject. A zero denominator produces
`0.0`, not a perfect score.

`RunEval` checks every `Source` quote for literal presence in the Article's
headline or body. It marks missing quotes as unsupported without asking the
judge. For present quotes, `LangChain4jClaimSupportJudge` returns a support
verdict; an unreadable verdict is indeterminate. The Markdown report includes
up to five deterministic citation decisions for manual review. The judge uses
the configured extraction model, so the sample remains important.

Input and output token rates in `config/application.yml` produce estimated cost
in USD. If usage or rates are unavailable, the report sets `cost.available` to
`0.0` and omits the cost amounts.

`RunEval` tests cover known metric values, one-to-one matching, empty and partial
Gold set inputs, citation support, and cost aggregation. Snapshot adapter tests
compare all 24 instrument quotes to the recorded 2026-10-28 snapshot fixture.
The eval store has a JSON/Markdown round-trip test and an in-memory test fake;
the HTTP routes have run, retrieval, not-found, and not-configured tests.
`JsonEvalStore` writes versioned JSON and Markdown files; the HTTP API exposes
the JSON report.

---

## 7. Key seams & the LangChain4j investigation

Two seams carry the project's risk and are kept deliberately narrow:

1. **`NewsItemExtractor`** — the LLM seam. The `NewsItem` shape (enums, lists,
   optional fields, mandatory `source`, provider divergence) is exactly what
   stresses LangChain4j's structured output. The upstream investigation is a
   **time-boxed spike** with explicit exit criteria (a reproducible finding +
   issue/PR link, or a documented "no issue found").
2. **`MarketDataFetcher`** — swappable and composite, so free-source coverage gaps can
   be patched per instrument type without touching the domain.

---

## 8. Tech stack

As per PRD §9: **Kotlin + Ktor + LangChain4j**, HTML via `kotlinx.html`, JSON
snapshots (no DB). LangChain4j provides the chat-model client (single
OpenAI-compatible endpoint) and JSON-object mode; the adapter owns the mapping
from the model's JSON answer to typed `NewsItem` candidates (§4).

---

## 9. Design principles

1. **Dependencies point inward, and the graph is acyclic.** Domain has zero
   outward deps; application never imports the adapters layer.
2. **One composition root.** `CompositionRoot` is the only place that constructs
   adapters and injects them into use cases; nothing else wires dependencies.
3. **Ports & adapters.** Production integrations sit behind domain ports; tests
   use fakes where controlled inputs are needed. Providers and data sources are
   implementation details behind `NewsItemExtractor`, `NewsSource`, and
   `MarketDataFetcher`.
4. **Deep use cases.** Each use case keeps orchestration behind one method;
   `GenerateDailyReport` is tested through port fakes.
5. **Invariants live in the types, not in pipeline steps** (parse, don't
   validate — §2.3).
6. **Numbers are never extracted, only looked up** (ADR-0001).
7. **Every claim is cited** — an uncited assertion is a failure, not output
   (ADR-0002).
8. **Providers are swappable by config** (ADR-0003).
9. **Wire format is an adapter concern.** The domain never imports
   `kotlinx.serialization`.
