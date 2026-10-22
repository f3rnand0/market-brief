# Architecture — market-brief

`market-brief` is a Kotlin service that produces a **daily summary of the most
important global investment-market news** for the previous day. It gathers free
news and market data, extracts the meaningful information into typed units
(`NewsItem`s), and assembles a templated report (JSON + HTML).

This document describes the intended architecture. It follows a
**Domain-Driven Design (DDD)** style with single-direction dependencies between
layers. It is the code-level counterpart to [`PRD.md`](PRD.md) (the *what*) and
[`GLOSSARY.md`](GLOSSARY.md) (the *vocabulary*).

> **Status: design only.** The repository currently contains the project
> skeleton (build files + empty layered packages + a `/health` endpoint). The
> components below are **not yet implemented**.

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
└───────────────┬──────────────────────────────┬───────────────┘
                │ uses                          │ implements
┌───────────────▼────────────────┐  ┌────────────▼──────────────┐
│            domain              │  │      infrastructure        │
│  NewsItem, Report, Snapshot,   │◄─┤  Yahoo/fallback market data│
│  value objects, validator,     │  │  RSS/GDELT news ingestion  │
│  ports (interfaces)            │  │  LangChain4j extractor      │
└────────────────────────────────┘  │  JSON report/eval store     │
                                    └────────────────────────────┘
```

| Layer          | Package                      | Depends on                |
|----------------|------------------------------|---------------------------|
| domain         | `com.marketbrief.domain`     | nothing (pure Kotlin)     |
| application    | `com.marketbrief.application`| domain                    |
| infrastructure | `com.marketbrief.infrastructure` | domain (+ 3rd-party libs) |
| presentation   | `com.marketbrief.http`       | application + domain      |

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
- `direction` — UP / DOWN / FLAT (for moves)
- `metrics` — extracted numbers with units (e.g. "−1.2%", "$42B")
- `source` — the article id + quote span(s) that support the assertion
  (**mandatory**; see ADR-0002)

**`Snapshot`** is the structured market-numbers section, grouped by asset class
(equities, fixed income, FX & volatility, commodities, crypto). Its numbers come
from a market-data API — **never** from article prose (ADR-0001).

**`Report`** is the assembled output document: the four sections built from a
`Snapshot` plus the extracted `NewsItem`s.

**`Article`** is the input aggregate: headline, body, `outlet`, URL, publication
date.

### 2.2 Two kinds of numbers

Kept strictly separate:

| Kind            | Origin          | Citation required? |
|-----------------|-----------------|--------------------|
| Snapshot values | market-data API | no (authoritative) |
| `NewsItem.metrics` | prose extraction | yes (`source`)  |

### 2.3 Domain services

- **`NewsItemValidator`** enforces the core invariant from ADR-0002: an
  assertion without a backing `source` is an *extraction failure*, not a valid
  output. Extraction candidates pass through this before reaching the report.
- **Section assembly rules** define how validated `NewsItem`s map to the four
  report sections: regional grouping (US / China / Europe / Latam / Asia), the
  company/sector balance (US / China / Global, never US-only), and the summary's
  S&P + Nasdaq moves and causes.

### 2.4 Ports (interfaces owned by the domain)

The seams where infrastructure plugs in. The domain *declares* them;
infrastructure *implements* them.

```kotlin
interface MarketData {                     // free market-data sources (ADR-0001)
    fun snapshot(instruments: Set<Instrument>): Snapshot
}

interface NewsSource {                     // free news ingestion
    fun fetch(window: DayWindow): List<Article>
}

interface NewsItemExtractor {              // the LLM seam (LangChain4j)
    fun extract(article: Article): List<NewsItem>
}

interface ReportStore {                    // persist / load reports
    fun save(report: Report)
    fun load(date: LocalDate): Report?
}
```

`NewsItemExtractor` is the most important seam: the LangChain4j adapter
implements it, and the structured-output investigation targets it directly
(see §7).

---

## 3. Application layer (`application/`)

Use cases orchestrate the ports and domain services.

- **`GenerateDailyReport`** — the pipeline:
  1. `MarketData.snapshot(...)` → `Snapshot`.
  2. `NewsSource.fetch(...)` → `Article`s.
  3. `NewsItemExtractor.extract(...)` → candidate `NewsItem`s.
  4. `NewsItemValidator` → keep valid, drop (and record) unsupported ones.
  5. Section assembly → `Report`.
  6. `ReportStore.save(...)`.
- **`RunEval`** — runs the gold set against the extractor and produces the
  versioned eval report (see §6).

A scheduler triggers `GenerateDailyReport` at 09:00 Europe/Berlin (CET/CEST
aware); the PoC also exposes an on-demand trigger.

---

## 4. Infrastructure layer (`infrastructure/`)

Concrete adapters implementing the domain ports.

- **`YahooMarketData`** — primary free market-data source, behind a composite
  adapter that applies **per-instrument-type fallbacks** (e.g. FRED for yields)
  and can run **multiple sources concurrently** (ADR-0001).
- **`RssNewsSource` / `GdeltNewsSource`** — free news ingestion (Google News RSS
  / GDELT backbone + curated RSS list).
- **`LangChain4jNewsItemExtractor`** — the LLM adapter. Uses a single
  **OpenAI-compatible** chat model (`baseUrl` + `model` + `apiKey` from env) so
  DeepSeek, Kimi, Ollama, OpenRouter, etc. are swappable (ADR-0003), with
  **structured output** mapping article text → typed `NewsItem`s.
- **`JsonReportStore`** — persists reports and eval results as JSON snapshots to
  disk (no DB in v1).

---

## 5. Presentation layer (`http/`)

Ktor server exposing a small JSON/HTML API. Domain objects are **not**
serialized directly; the layer maps to its own `@Serializable` DTOs.

| Method | Path                       | Purpose                          |
|--------|----------------------------|----------------------------------|
| GET    | `/health`                  | Liveness                         |
| POST   | `/report/generate`         | Trigger `GenerateDailyReport`    |
| GET    | `/report/{date}`           | JSON report                      |
| GET    | `/report/{date}.html`      | Rendered HTML (kotlinx.html)     |
| POST   | `/eval/run`                | Trigger `RunEval`                |
| GET    | `/eval/{id}`               | Eval report                      |

---

## 6. Evaluation pipeline

A fixed, gold-labeled dataset measures the extractor and drives improvement
(PRD §11):

- **Gold set** — per-article `gold/<id>.json` with expected `NewsItem`s and
  expected snapshot facts.
- Metrics: **accuracy** (field-level precision/recall), **unsupported claims**
  (share of assertions whose cited source actually supports them), **missing
  values** (recall of expected-but-omitted items).
- Output: a **versioned eval report**, feeding the loop
  measure → root-cause → change → re-measure.

---

## 7. Key seams & the LangChain4j investigation

Two seams carry the project's risk and are kept deliberately narrow:

1. **`NewsItemExtractor`** — the LLM seam. The `NewsItem` shape (enums, lists,
   optional fields, mandatory `source`, provider divergence) is exactly what
   stresses LangChain4j's structured output. The upstream investigation is a
   **time-boxed spike** with explicit exit criteria (a reproducible finding +
   issue/PR link, or a documented "no issue found").
2. **`MarketData`** — swappable and composite, so free-source coverage gaps can
   be patched per instrument type without touching the domain.

---

## 8. Design principles

1. **Dependencies point inward.** Domain has zero outward deps.
2. **Ports & adapters.** The LLM provider and data sources are implementation
   details behind `NewsItemExtractor` and `MarketData`.
3. **Numbers are never extracted, only looked up** (ADR-0001).
4. **Every claim is cited** — an uncited assertion is a failure, not output
   (ADR-0002).
5. **Providers are swappable by config** (ADR-0003).
6. **Wire format is an adapter concern.** The domain never imports
   `kotlinx.serialization`.
