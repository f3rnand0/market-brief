# LangChain4j structured-output spike

## Status

The spike uses the deterministic corpus in `src/main/resources/corpus/articles.json`
and the corresponding labels in `src/main/resources/gold/`. Live provider runs are
opt-in and must be launched through 1Password:

```bash
op run --env-file=config/secrets.env -- ./gradlew liveTest
```

`OPENCODE_API_KEY` is a service-account credential stored in 1Password. It must
never be committed or printed in probe output.

## Probe contract

Each selected model receives the same article and extraction prompt. Results are
checked against the article body for citation support, against the gold set for
field-level expectations, and against the JSON contract used by
`LangChain4jNewsItemExtractor`.

The selected OpenCode Go models are `deepseek-v4.1-flash`, `glm-5.3-flash`, and
`gpt-6-luna`. Free models are excluded from the planned comparison. The Go API has a separate route from the general Console
inference API: Chat Completions uses
`https://opencode.ai/inference/go/openai/v1/chat/completions`, while GPT uses
`https://opencode.ai/inference/go/openai/v1/responses`. Requests authenticate
with `OPENCODE_API_KEY` and include a client User-Agent and stable
`x-opencode-session` header.

The live model catalog can be queried at
`https://opencode.ai/inference/go/v1/models`. The general catalog and general
inference routes are not substitutes for the Go routes.

DeepSeek V4.1 requires the workspace's allowed region to include Global. If the
workspace is configured for Europe and United States only, the Go API rejects
that model before inference.

An initial zero-cost smoke request to `beam-free` succeeded, but used invented
enum labels before the prompt was tightened; free models are not part of the
planned comparison. `iceman-contributor-free` was denied because the workspace
privacy setting does not allow endpoints that train on request data.

With the Go routes and schema-constrained prompt, the full corpus comparison
completed on 2026-11-03. Each model processed all 40 articles (120 requests
total). GPT-6 Luna used the Responses API; DeepSeek and GLM used Chat
Completions. The GPT-6 Luna raw responses are in
`build/reports/langchain4j-opencode.md`; the DeepSeek and GLM responses for
the 32 newly discovered headlines are in
`build/reports/langchain4j-open-candidates.md`.

| Model | Responses | Parsed JSON | Items | Invalid enum values | Unsupported citation quotes | Gold matches* | Go allowance |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `deepseek-v4.1-flash` | 40 | 40 | 48 | 0 | 0 | 6/8 | $0.00830681 |
| `glm-5.3-flash` | 40 | 40 | 41 | 0 | 0 | 5/8 | $0.00434980 |
| `gpt-6-luna` | 40 | 40 | 45 | 0 | 0 | 6/8 | $0.02374120 |

All three models returned only valid JSON for all articles, used the domain enum
values, and emitted citation quotes found verbatim in each article body. Direct
cost was reported as zero; the Go allowance amounts above were reported in
`usage.opencode.allowance`.

At the time of the model runs, only 8 articles had gold labels (8 expected
items). *Gold matches use a preliminary item match requiring equal type and
region plus at least one overlapping subject: DeepSeek matched 6, GLM 5, and
GPT-6 Luna 6. This is not the project's official field-level eval. Direct cost
was reported as zero and Go allowance totaled $0.03639781.

The 32 RSS records contained Google News headlines, not publisher-page text.
Their headline-based labels were removed from the active Gold set. The packaged
corpus now contains only the 8 curated Articles with full text. Live RSS/GDELT
candidates must pass publisher-page text extraction before they become Articles
or receive Gold labels.

## Exit criteria

Document observed-versus-expected behavior for all three models, including JSON
shape, enums, optional fields, nested citations, malformed output, and provider
errors. If a reproducible LangChain4j or provider defect is found, record a
sanitized fixture and an upstream issue or PR link. Otherwise explicitly record
that no issue was found for the probes run.
