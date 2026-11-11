# PRD — Daily Global Market Summary

## 1. Problem & purpose

Investors need a concise daily account of global investment markets for the
previous day. The service gathers news and market data from free sources,
extracts cited news items, and assembles a structured report.

## 2. Goals

- Produce a daily report with a summary, market snapshot, regional news, and
  company and sector news.
- **Discover** articles deterministically from RSS feeds and GDELT; the
  evaluation pipeline uses a fixed, packaged corpus.
- **Fetch publisher-page text** for each live news candidate before extraction.
  A headline or RSS teaser alone is not an eligible `Article`; skip candidates
  when publisher text cannot be retrieved or extracted.
- **Extract** each article into typed `NewsItem`s via an LLM with structured
  output.
- Measure extraction quality against a fixed labeled corpus; use the results to
  improve accuracy, citation support, and completeness.
- Use free sources for news and market data. LLM API usage may incur
  pay-as-you-go costs.

## 3. Non-goals (v1)

- No paid news or market-data subscriptions. Pay-as-you-go LLM API usage is in
  scope; fixed-fee LLM subscriptions are out of scope.
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

1. **Brief summary** — S&P 500 and Nasdaq Composite levels and moves (and their
   cause), plus other important news. Levels and moves come from the snapshot;
   the cause is cited LLM prose. Missing index data is reported as unavailable,
   never as "no moves".
2. **Market snapshot** — numbers and day-over-day changes for the instrument
   set, grouped by asset class when rendered. Fixed income shows the latest
   yield with the daily move in basis points and percent
   (`4.995 (+0.9 bp, +0.18%)`); other asset classes show the percentage change.
   Fully structured, sourced from the market-data API (no LLM).
3. **Regional news** — the most relevant market news per region: US, China,
   Europe, Latam, Asia, with at most four items per region.
4. **Company / sector news** — the most relevant news about the biggest
   companies and sectors, up to four items balanced across US / China / Global
   (not US-only). A company appears at most once in this section.
   Each `NewsItem` appears in exactly one section: company and sector items
   are never duplicated into the regional sections.

Global macro and market news in the other-news section is capped at four items.
Duplicate news items with the same type and subjects are removed across the
report before section selection.

## 6. Information integrity

Use [`GLOSSARY.md`](GLOSSARY.md) for canonical domain terms. Every extracted
`NewsItem` needs article quotes supporting its assertion. Snapshot values come
from market-data APIs; LLM extraction does not supply market numbers.

## 7. Instrument set (snapshot)

- **Equities**: S&P 500, Nasdaq Composite, STOXX Europe 600, FTSE 100, DAX 40,
  Nikkei 225, Shanghai Composite, Hang Seng
- **Fixed Income**: US 10Y, Germany 10Y, France 10Y, UK 10Y, Japan 10Y, China 10Y
- **FX & Volatility**: EUR/USD, USD/CNY, VIX
- **Commodities**: Gold, Silver, Copper, Brent, WTI
- **Crypto**: Bitcoin, Ethereum

## 8. Data sources (free only)

- Use free market-data APIs and deterministic RSS/GDELT discovery. Current
  source wiring is documented in [`ARCHITECTURE.md`](ARCHITECTURE.md).

## 9. Tech stack

Kotlin + Ktor + LangChain4j. A single **OpenAI-compatible** chat-model
abstraction (`baseUrl` + `model` + `apiKey` from env) so DeepSeek, Kimi,
Ollama, OpenRouter, etc. are swappable. Extraction uses LangChain4j
**structured output** → typed `NewsItem`s. HTML rendering via `kotlinx.html`.

## 10. Schedule and persistence

- Runs daily at **09:00 Europe/Berlin** (CET/CEST aware). The data window is
  the **previous calendar day**.
- Reports and evaluation results are stored as JSON files; no database is
  required.

## 11. Evaluation & improvement loop

- **Gold set**: manually labeled expected `NewsItem`s for packaged Articles.
  Snapshot values are market-data adapter outputs, not LLM
  extraction results; verify them with recorded market-data fixtures instead of
  including them in the extraction eval.
- Metrics:
  - **accuracy** — field-level precision/recall vs gold.
  - **unsupported claims** — share of assertions whose cited source actually
    supports them (LLM-as-judge + manual sample).
  - **missing values** — recall of expected-but-omitted items.
  - **cost** — estimated LLM spend per report and per `NewsItem`, based on
    reported token counts and configured token rates. Report cost as unavailable
    when usage or rates are missing; do not substitute zero.
- Output: a **versioned eval report** (JSON + Markdown).
- Loop: measure → root-cause → change → re-measure.

The evaluation inputs, metrics, and limitations are documented in
[`ARCHITECTURE.md`](ARCHITECTURE.md).

## 12. Out of scope

Email delivery, multi-user, database persistence, paid news or market-data
services, intraday updates, and AI-agent web browsing (open-ended discovery).
