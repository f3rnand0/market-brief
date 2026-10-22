# PRD — Daily Global Market Summary

## 1. Problem & purpose

Investors need one short, daily read of what mattered in global investment
markets over the previous day. This service produces that summary
automatically: it gathers **free** news and market data from the web, extracts
the meaningful information into typed units, and assembles a templated report.

## 2. Goals

- Produce a daily report with four sections (summary, snapshot, regional news,
  company/sector news).
- **Discover** articles deterministically from free sources (Google News RSS /
  GDELT + a curated RSS list; PoC: a static curated corpus).
- **Extract** each article into typed `NewsItem`s via an LLM with structured
  output.
- Convert articles into typed units of information that fill the report via a
  template.
- Measure extraction quality (accuracy, unsupported claims, missing values)
  against a fixed dataset and evals, root-cause issues, and improve
  effectiveness, integrity, and completeness.
- Use **free sources only** for data and news.

## 3. Non-goals (v1)

- No paid news/data subscriptions; no fixed-fee LLM subscriptions.
  Pay-as-you-go open-model API usage (DeepSeek, Kimi, …) is in scope and
  budgeted.
- No real-time intraday updates — the report is a daily batch.
- No multi-user auth — internal tool.
- No database — JSON snapshots to disk.

## 4. Audience

Internal team; English; concise. Factual integrity matters more than editorial
polish.

## 5. Output / deliverable

- A structured **report** as JSON (the source of truth, and what evals score).
- A rendered **HTML page** (kotlinx.html) presenting the four sections.
- Email delivery is out of scope for v1.

### 5.1 The four sections

1. **Brief summary** — S&P 500 and Nasdaq Composite moves (and their cause),
   plus other important news. LLM prose; every claim cited.
2. **Market snapshot** — numbers and day-over-day percentages for the
   instrument set, grouped by asset class. Fully structured, sourced from the
   market-data API (no LLM).
3. **Regional news** — the most relevant market news per continent: US, China,
   Europe, Latam, Asia.
4. **Company / sector news** — the most relevant news about the biggest
   companies and sectors, balanced across US / China / Global (not US-only).

## 6. Domain model

See `GLOSSARY.md` for the canonical vocabulary. Highlights:

- **`NewsItem`** is the extracted unit: `type` ∈ { MARKET_MOVE, COMPANY,
  SECTOR, MACRO }, a `region` tag, `subjects`, an `assertion`, an optional
  `direction`, optional `metrics`, and a mandatory `source` citation.
- Two kinds of numbers, kept strictly separate: **snapshot numbers** (from the
  API, authoritative, no citation needed) vs **`NewsItem.metrics`** (extracted
  from prose, citation required).
- A `MARKET_MOVE` carries an optional `cause` (free text) that must itself be
  source-backed.

## 7. Instrument set (snapshot)

- **Equities**: S&P 500, Nasdaq Composite, STOXX Europe 600, FTSE 100, DAX 40,
  Nikkei 225, Shanghai Composite, Hang Seng
- **Fixed Income**: US 10Y, Germany 10Y, France 10Y, UK 10Y, Japan 10Y, China 10Y
- **FX & Volatility**: EUR/USD, USD/CNY, VIX
- **Commodities**: Gold, Silver, Copper, Brent, WTI
- **Crypto**: Bitcoin, Ethereum

## 8. Data sources (free only)

- **Market data**: Yahoo Finance primary, with **per-instrument-type
  fallbacks** (e.g. FRED for yields), behind a swappable `MarketData` port that
  can run **multiple sources concurrently**.
- **News**: Google News RSS / GDELT as the coverage backbone, plus a curated
  RSS list of free outlets. The PoC uses a **static curated corpus** (~30–40
  articles) spanning all regions, asset classes, and company/sector news,
  deliberately seeded with unsupported-claim and missing-value traps.

## 9. Tech stack

Kotlin + Ktor + LangChain4j. A single **OpenAI-compatible** chat-model
abstraction (`baseUrl` + `model` + `apiKey` from env) so DeepSeek, Kimi,
Ollama, OpenRouter, etc. are swappable. Extraction uses LangChain4j
**structured output** → typed `NewsItem`s. HTML rendering via `kotlinx.html`.

## 10. Scheduling & ops

- Runs daily at **09:00 Europe/Berlin** (CET/CEST aware). The data window is
  the **previous day** (24h ending at generation time).
- PoC: on-demand trigger endpoint; JSON snapshots of reports and evals to disk;
  no DB.

## 11. Evaluation & improvement loop

- **Gold set**: a per-article `gold/<id>.json` listing expected `NewsItem`s and
  expected snapshot facts, manually labeled.
- Metrics:
  - **accuracy** — field-level precision/recall vs gold.
  - **unsupported claims** — share of assertions whose cited source actually
    supports them (LLM-as-judge + manual sample).
  - **missing values** — recall of expected-but-omitted items.
  - **cost** — LLM spend per report and per `NewsItem`.
- Output: a **versioned eval report** (JSON + Markdown).
- Loop: measure → root-cause → change → re-measure.

## 12. Upstream LangChain4j investigation (time-boxed spike)

Probe LangChain4j structured output and provider behaviour using the `NewsItem`
shape (enums, lists, optional fields, citations, and divergence across
DeepSeek / Kimi / open models).

- **Exit criteria**: a minimal reproducer + documented observed-vs-expected +
  an upstream issue/PR link, **or** a documented "no issue found" with the
  probes that were run.
- **Time-boxed**; must not block the main report work.

## 13. Release plan

1. PoC — static corpus + market-data fetch + LLM extraction → JSON report →
   HTML page.
2. Eval harness + gold set → first eval report.
3. Live web ingestion (free sources) + scheduled run.
4. Upstream investigation spike (runs in parallel with 1–3).

## 14. Out of scope (v1)

Email delivery, multi-user, DB persistence, paid sources, intraday updates,
AI-agent web browsing (open-ended discovery).

## 15. To resolve during implementation

- Exact curated RSS outlet list (including China and Latam outlets).
- Exact per-instrument-type fallback mapping for market data.
- Which judge model/approach scores "unsupported claims".
