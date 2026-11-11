# Architecture — market-brief

`market-brief` is a Kotlin service that produces a **daily summary of the most
important global investment-market news** for the previous day. It gathers free
news and market data, extracts the meaningful information into typed units
(`NewsItem`s), and assembles a templated report (JSON + HTML).

This document describes the implemented architecture and current limitations.
Dependencies point inward: the domain owns
business rules and ports, adapters implement those ports, and the composition
root wires the application. The [PRD](PRD.md) states product requirements;
the [glossary](GLOSSARY.md) defines domain terms.

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
│  starts the scheduler + Ktor. The only wiring object that     │
│  imports both application and adapters.                       │
└──────────────────────────────────────────────────────────────┘
```

There is **no edge between application and adapters**. Use cases depend
on domain ports; adapters implement them; the composition root connects the
two. That is what keeps the dependency graph acyclic.

The composition root loads settings and secrets, then passes plain config
objects to adapters. See [configuration](../README.md#configuration) for local
setup and ADR-0003 for the provider boundary.

| Layer | Package | Depends on |
| --- | --- | --- |
| Domain | `com.marketbrief.domain` | None |
| Application | `com.marketbrief.application` | Domain, coroutines |
| Adapters | `com.marketbrief.adapters` | Domain, report format, external libraries |
| Report format | `com.marketbrief.reportformat` | Domain |
| Presentation | `com.marketbrief.http` | Application, report format |
| Scheduling | `com.marketbrief.scheduling` | Coroutines, `java.time`, SLF4J |
| Configuration | `com.marketbrief.config` | kaml, kotlinx.serialization |
| Composition root | `com.marketbrief` | Application, adapters, domain, HTTP, scheduling, configuration, Ktor |

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

The extractor adapter maps raw LLM output to `NewsItem`, dropping and counting
candidates that violate domain invariants. Callers receive only valid items;
the application layer does not repeat this validation.

### 2.4 Section assembly rules

Section assembly follows these rules:

- The summary takes S&P 500 and Nasdaq Composite values from `Snapshot` and
  causes from matching `MARKET_MOVE` items. Missing index data renders as
  `"<index>: data unavailable."`; a complete snapshot without index quotes
  renders "No major index moves.".
- Global `MACRO` and `MARKET_MOVE` items go to `Report.otherNews`.
- `COMPANY` and `SECTOR` items go only to the company/sector section. Items are
  balanced round-robin across US, China, and Global, up to ten items.
- Other regional items exclude Global and company/sector items. They are sorted
  by publication date, then outlet and Article ID. Duplicate events are
  removed; each region has a five-item limit and a two-item-per-outlet limit.

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

The LangChain4j adapter implements `NewsItemExtractor`; the completed
structured-output model comparison is documented in
[the Gold-set model comparison](langchain4j-gold-set-model-comparison.md).

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
  coordinates `NewsItemMetricsEvaluator`, `CitationMetricsEvaluator`, and
  `EvalReportFormatter`, then saves a versioned eval report (see §6). Evaluation
  is sequential. The composition root supplies `JsonCorpusNewsSource` so eval
  uses the packaged Articles referenced by the Gold set, not the live RSS/GDELT
  source. `RunEval` fails if no Gold set Articles match or if any are missing.
  Item matching, citation scoring, and Markdown formatting live in their
  respective collaborators.

`GenerateDailyReport.generate` is `suspend`: the `MarketDataFetcher` and
`NewsItemExtractor` ports are suspending, and the HTTP layer calls the use case
from its request coroutine. Concurrent calls for the same report date share one
in-flight run; the run is detached from the requesting coroutine, so a
disconnected client cannot cancel work shared by another caller.

A scheduler (wired in the composition root, not part of the use case) triggers
`GenerateDailyReport` at 09:00 Europe/Berlin (CET/CEST aware); an HTTP endpoint
also supports on-demand runs. `DailyReportScheduler` owns no business logic: it
takes the current instant from an injected `Clock`, sleeps until the next 09:00
in the Berlin zone, runs the supplied action, and repeats. A failed run is
logged and the schedule continues, so one provider outage does not silence the
next day's report.

---

## 4. Adapters layer (`adapters/`)

Concrete adapters implementing the domain ports.

- **`YahooQuoteFetcher` / `CnbcQuoteFetcher`** — implement the internal
  `QuoteFetcher` seam. `CompositeMarketDataFetcher` is the single
  `MarketDataFetcher` adapter: Yahoo is primary, and the CNBC fixed-income
  fallback fills gaps. Both sources are queried concurrently, and the
  primary wins when two sources cover the same instrument (ADR-0001). Requests
  are bounded at 15 seconds; CNBC decodes each quote independently so one
  malformed element does not discard the batch. The composite records
  unresolved instruments in `Snapshot.missing` and logs the gap.
- **`RssNewsSource` / `GdeltNewsSource`** — live sources wired through
  `CompositeNewsSource`. RSS currently uses BBC, Guardian, CNBC, and MercoPress;
  GDELT uses its document API. Both fetch publisher-page text before creating an
  `Article`. `PublisherArticleTextFetcher` selects known body containers or
  `article` / `main` content and requires at least 80 words. It accepts only
  HTTP URLs and rejects Google News redirects. Candidates with inaccessible or
  short pages are skipped; the word-count floor filters headlines and teasers
  but does not establish that the full story was captured. Individual page and
  feed failures are logged. A failed feed or source is tolerated when another
  succeeds; if all fail, `NewsFeedUnavailableException` fails report
  generation. A successful empty feed is a valid empty result. `NewsSource.fetch`
  is synchronous, so the HTTP adapters use `runBlocking`. Evaluation instead
  uses `JsonCorpusNewsSource` to resolve Gold-set Article IDs.
- **`LangChain4jNewsItemExtractor`** — the LLM adapter. It uses one
  OpenAI-compatible model (ADR-0003), requests JSON-object output, and parses
  the response with kotlinx.serialization. The input Article supplies the
  citation's `articleId`; the model supplies quote spans. Candidates that fail
  domain invariants are dropped and counted as `droppedCandidateCount`. The
  adapter returns token usage and estimated cost when rates are configured.
  Its async chat call suspends without blocking a thread. Provider and transport
  failures propagate; empty or malformed content yields no items for that
  Article.
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

`NewsItemMetricsEvaluator` owns exact and field-level matching,
`CitationMetricsEvaluator` owns quote checks and semantic support scoring, and
`EvalReportFormatter` renders the metrics and citation review samples. `RunEval`
coordinates these collaborators and persists the report.

Input and output token rates in `config/application.yml` produce estimated cost
in USD for the configured model. The estimate uses the catalog's standard
rates; it does not account for cached-input discounts or time-based pricing
multipliers. If usage or rates are unavailable, the report sets
`cost.available` to `0.0` and omits the cost amounts.

`JsonEvalStore` writes versioned JSON and Markdown files; the HTTP API exposes
the JSON report.

---
