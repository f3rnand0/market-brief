# OpenAI-compatible provider abstraction for the LLM

The extractor must work with DeepSeek or Kimi, plus other open models. We use a
single OpenAI-compatible chat model configured by `baseUrl` + `model` +
`apiKey`, so providers are swappable without code changes. This avoids lock-in
to any one provider and enables diffing providers for the structured-output
investigation.

The two kinds of configuration are stored differently:

- **Non-sensitive settings** — `baseUrl`, `model`, timeouts, retries, and the
  name of the environment variable that carries the key — live in
  `config/application.yml`. They are reviewable in git. The composition root
  loads them at startup.
- **The API key** is a secret and is never stored in the repository. It is
  injected at runtime by 1Password's `op run`, which resolves `op://`
  references from `config/secrets.env` into environment variables. The
  composition root reads the named variable and passes the key to the adapter
  in `OpenAiCompatibleConfig`. Any other secret injector works too.

The composition root resolves both and hands the adapter a plain
`OpenAiCompatibleConfig`, so the adapter stays ignorant of files and
environment variables.
