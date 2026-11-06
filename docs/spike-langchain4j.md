# LangChain4j structured-output spike

## Purpose

This completed probe checked the production `LangChain4jNewsItemExtractor`
against the `NewsItem` JSON contract using OpenAI-compatible models. It is
opt-in and writes aggregate observations to
`build/reports/langchain4j-opencode.md`; raw model responses and credentials are
excluded from that report.

Run it with the OpenCode Go credential injected from 1Password:

```bash
SPIKE_ARTICLE_LIMIT=2 op run --env-file=config/secrets.env -- \
  ./gradlew liveTest --tests '*LangChain4jOpenCodeSpikeTest'
```

The default models are `deepseek-v4.1-flash` and `glm-5.3-flash`. Set
`SPIKE_MODELS` to select models, `SPIKE_ARTICLE_LIMIT` to cap requests,
`SPIKE_UNLABELED_ONLY=true` to skip labeled Articles, or `SPIKE_REPORT` to set
the report path. The test requires `OPENCODE_API_KEY`. It calls the OpenCode Go
Chat Completions route and adds User-Agent and session headers. Models that
require the Responses API are outside this probe.

## Expected behavior and evidence

| Area | Expected behavior | Evidence and observed behavior |
| --- | --- | --- |
| JSON shape | A JSON object with an `items` array becomes typed domain items. Malformed JSON yields no items. | Live results are summarized in the generated report. The stubbed adapter test confirms malformed output yields an empty extraction. |
| Enums | `type`, `region`, and optional `direction` use domain enum values. Invalid values are dropped and counted. | The adapter maps valid values to enums; the stubbed test confirms an invalid region is dropped. Live dropped-candidate totals are recorded per model. |
| Optional fields | Missing `direction` and `cause` map to `null`; missing `metrics` maps to an empty list. | A stubbed test verifies all three defaults on an otherwise valid item. Live counts of unset fields and empty metrics appear per model. |
| Nested citations | `source.quotes` is a non-empty list of verbatim spans; the adapter supplies the current Article ID. | Stubbed tests verify nested mapping and the Article ID. Live results count quotes and verify each against the source Article body. |
| Provider errors | Authentication and rate-limit failures propagate; they must not become empty successful results. | Stubbed tests confirm HTTP 401 and 429 errors propagate. The live report records failures by exception class only; response bodies are omitted. |

Live item counts measure schema parsing, not semantic accuracy. Citation
support is checked against the Article body. The evaluation pipeline measures
Gold-set matching and semantic citation support; see
[`ARCHITECTURE.md`](ARCHITECTURE.md#6-evaluation-pipeline).

## Live LangChain4j results

Run on 2026-11-06 through the production extractor, with a two-Article cap to
bound provider requests. The generated report contained per-Article details;
the results below are the retained summary. No raw model response was saved.

| Model | Articles | Provider errors | Typed items | Items missing direction | Items missing cause | Items with empty metrics | Citation quotes | Unsupported quotes | Dropped candidates | Usage metadata |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `deepseek-v4.1-flash` | 2 | 0 | 8 | 2 | 2 | 2 | 11 | 0 | 0 | 2/2 |
| `glm-5.3-flash` | 2 | 0 | 5 | 1 | 1 | 0 | 6 | 0 | 0 | 2/2 |

Both models returned typed items for Articles `article-001` and `article-002`.
Every emitted quote was present verbatim in its Article. Missing optional
fields were accepted by the extractor; invalid candidates were not observed.
These are schema and citation checks, not a semantic-quality judgment. The
probe can run against the full packaged corpus by omitting
`SPIKE_ARTICLE_LIMIT`.

## Conclusion

No issue was found in the tested adapter behavior. Both models returned valid
typed items and quotes present in the two sampled Articles; neither dropped
candidates nor provider errors occurred. Stubbed adapter checks cover malformed
output and authentication/rate-limit errors. This result applies only to the
two models, two Articles, and behaviors listed above.
