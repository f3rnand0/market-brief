# Extraction fans out concurrently, bounded

`GenerateDailyReport` extracts articles **concurrently**, at most four in flight
(`extractionParallelism`), instead of serially. Each Article is an independent
LLM call, so bounded concurrency reduces the latency added by sequential
round-trips.

Each Article receives its own request. `awaitAll` restores input order before
section assembly, so completion timing does not change report ordering.

The permit count is a constructor parameter the composition root can tune per
provider. LangChain4j retries transient failures and uses its asynchronous chat
API, so each permit holds a pending request rather than a blocked thread. Empty
or malformed model output contributes no items for that Article. Provider or
transport failures — authentication, missing model, rate limiting, server
errors, or unreachable endpoints — fail the run, which saves no report.
