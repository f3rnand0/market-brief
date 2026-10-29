# Extraction fans out concurrently, bounded

`GenerateDailyReport` extracts articles **concurrently**, at most four in flight
(`extractionParallelism`), instead of serially. Each article is an independent
LLM round-trip; serial extraction made the report's latency the sum of every
round-trip for the 30–40-article corpus.

This preserves the eval loop's reproducibility: requests are per article, so
prompts and responses are unchanged; `awaitAll` restores article order before
section assembly, so the assembled report is identical to the serial one. Cost
is unchanged — the same requests are made, just more of them at once.

The bound exists because providers rate-limit: unbounded fan-out would trade
latency for `429`s. The permit count is a constructor parameter so the
composition root can tune it per provider, and LangChain4j retries transient
failures. Requests use LangChain4j's asynchronous chat API, so a permit holds a
pending request rather than a blocked thread. An article whose model output is
empty or malformed contributes no items without failing the report. Provider
and transport failures — authentication, missing model, rate limiting, server
errors, unreachable endpoints — propagate instead of producing a report with
no news, and a failed run saves nothing.

Rejected alternative: keeping extraction serial for cost and stable output. Cost
is the same either way, and order is preserved by construction, so serial
extraction bought nothing but latency.
