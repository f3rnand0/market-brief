# LangChain4j structured-output spike

## Purpose and status

This time-boxed spike checks whether the production LangChain4j OpenAI adapter
can handle the `NewsItem` JSON contract across OpenAI-compatible models. The
probe is opt-in and uses the same `LangChain4jNewsItemExtractor` as report
generation. It writes aggregate, sanitized observations to
`build/reports/langchain4j-opencode.md`; raw model responses and credentials are
not written.

Run it with the OpenCode Go credential injected from 1Password:

```bash
op run --env-file=config/secrets.env -- \
  ./gradlew liveTest --tests '*LangChain4jOpenCodeSpikeTest'
```

The default models are `deepseek-v4.1-flash` and `glm-5.3-flash`. Set
`SPIKE_MODELS` to a comma-separated list to change them, `SPIKE_ARTICLE_LIMIT`
to cap requests for a quick probe, `SPIKE_UNLABELED_ONLY=true` to probe only
Articles without Gold labels, or `SPIKE_REPORT` to choose the report path. The
test requires `OPENCODE_API_KEY`. The provider's OpenAI-compatible Chat
Completions route is `https://opencode.ai/inference/go/openai/v1`; the test
adds a User-Agent and session header. Models requiring the Responses API are
not included in this LangChain4j `OpenAiChatModel` probe.

## Expected behavior and evidence

| Area | Expected behavior | Evidence and observed behavior |
| --- | --- | --- |
| JSON shape | A JSON object with an `items` array becomes typed domain items. Malformed JSON yields no items. | Live results are summarized in the generated report. The stubbed adapter test confirms malformed output yields an empty extraction. |
| Enums | `type`, `region`, and optional `direction` use domain enum values. Invalid values are dropped and counted. | The adapter maps valid values to enums; the stubbed test confirms an invalid region is dropped. Live dropped-candidate totals are recorded per model. |
| Optional fields | Missing `direction` and `cause` map to `null`; missing `metrics` maps to an empty list. | A stubbed test verifies all three defaults on an otherwise valid item. Live counts of unset fields and empty metrics appear per model. |
| Nested citations | `source.quotes` is a non-empty list of verbatim spans; the adapter supplies the current Article ID. | Stubbed tests verify nested mapping and the Article ID. Live results count quotes and verify each against the source Article body. |
| Provider errors | Authentication and rate-limit failures propagate; they must not become empty successful results. | Stubbed tests confirm HTTP 401 and 429 errors propagate. The live report records failures by exception class only; response bodies are omitted. |

Live item counts measure schema parsing, not semantic accuracy. Citation
support is checked against the Article body. Gold-set semantic matching belongs
to the M7 eval harness.

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

## Earlier direct API comparison

An earlier probe on 2026-11-03 sent hand-built HTTP requests directly to
OpenCode, bypassing LangChain4j. It processed 40 articles per model. The
reported JSON, enum, and citation observations below are useful provider
context, but are not evidence about LangChain4j's request or response handling.
The raw output files were generated under `build/reports` and are not present
in the current checkout.

| Model | Responses | Parsed JSON | Items | Invalid enum values | Unsupported citation quotes | Preliminary Gold matches | Go allowance |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `deepseek-v4.1-flash` | 40 | 40 | 48 | 0 | 0 | 6/8 | $0.00830681 |
| `glm-5.3-flash` | 40 | 40 | 41 | 0 | 0 | 5/8 | $0.00434980 |
| `gpt-6-luna` | 40 | 40 | 45 | 0 | 0 | 6/8 | $0.02374120 |

Those preliminary Gold matches used equal type and region plus one overlapping
subject; they are not the project's official field-level metrics. At that time
only eight Articles had labels. Free-model endpoint and privacy errors were
also observed in the direct probe, but are outside the current model set.

## Conclusion

**No issue found for the probes run.** The production LangChain4j adapter
processed two corpus Articles with each of the two probed models. The returned
items had valid domain enums, mapped optional fields, non-empty nested citations,
and quotes present in their source Articles. No candidates were dropped and no
provider errors occurred. Separate stubbed adapter tests confirm malformed
output is converted to an empty extraction and authentication/rate-limit errors
propagate. The probe found no reproducible LangChain4j or provider defect, so
there is no upstream issue or PR to link. This conclusion is limited to these
two models, two Articles, and the tested adapter behavior.
