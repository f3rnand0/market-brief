# LangChain4j Gold-set model comparison

## Purpose

This opt-in live test compares OpenAI-compatible models on the packaged Gold
set using the production `RunEval` pipeline. Every model receives the same
labeled Articles and scoring logic. The claim-support judge is shared across
all model runs, so its model must stay fixed during a comparison.

The test writes a summary and each model's complete Eval report to
`build/reports/langchain4j-gold-set.md`. It does not persist Eval reports to the
normal report store. Raw model responses and credentials are excluded.

Run it with the OpenCode Go credential injected from 1Password:

```bash
op run --env-file=config/secrets.env -- ./gradlew liveTest --tests '*LangChain4jGoldSetModelComparisonTest'
```

The extraction models are `deepseek-v4.1-flash` and `glm-5.3-flash`; the fixed
claim-support judge is `minimax-m3`. The model names and report path are
constants in the test. The judge is separate from the extraction candidates.
The test requires `OPENCODE_API_KEY`. It calls the OpenCode Go Chat Completions
route. Models that require the Responses API are outside this comparison.

## Token rates

The rates below are USD per million tokens, from the OpenCode Go model catalog
(`GET /models`, checked 2026-11-11):

| Model | Input | Output |
| --- | ---: | ---: |
| `deepseek-v4.1-flash` | $0.15 | $0.60 |
| `glm-5.3-flash` | $0.15 | $0.50 |
| `minimax-m3` | $0.30 | $1.20 |

The application config sets the DeepSeek rates for the configured production
model. The comparison test supplies each model's rates separately, including
the judge. These estimates use standard input/output rates; they do not account
for cached-input discounts or DeepSeek's time-based price multiplier.

## Difference from `RunEval`

`RunEval` evaluates one configured extractor and saves one versioned Eval
report for normal application use. This test is an experiment runner: it
invokes `RunEval` once for each selected extraction model, shares a single
judge model, and writes a side-by-side comparison without saving those reports
to the normal Eval store. It deliberately reuses `RunEval` rather than
implementing another scoring path.

The test establishes relative results on this fixed corpus and these selected
models. It does not declare a universally better model. Gold-set item matching
compares outputs with manual labels, while claim-support results depend on the
fixed judge; use the included deterministic review samples when interpreting
those scores. Model calls are live and may incur provider costs.
