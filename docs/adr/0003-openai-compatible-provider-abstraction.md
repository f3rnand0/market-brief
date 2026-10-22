# OpenAI-compatible provider abstraction for the LLM

The extractor must work with DeepSeek or Kimi, plus other open models. We use a
single OpenAI-compatible chat model configured by `baseUrl` + `model` +
`apiKey` from environment, so providers are swappable without code changes.
This avoids lock-in to any one provider and enables diffing providers for the
structured-output investigation.
