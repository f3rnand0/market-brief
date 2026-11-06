# OpenAI-compatible provider abstraction for the LLM

The extractor uses one OpenAI-compatible chat model configured by endpoint,
model name, and API key. Switching compatible providers requires configuration
changes rather than adapter changes.

The two kinds of configuration are stored differently:

- Non-secret settings live in `config/application.yml`.
- The API key is injected at runtime through the environment. The composition
  root reads the configured variable and passes a plain `OpenAiCompatibleConfig`
  to the adapter. The repository stores no API key.

This keeps file and environment access in the composition root.
