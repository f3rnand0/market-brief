# market-brief

A Kotlin service that generates a daily summary of the most important global
investment-market news — index moves, an asset-class snapshot, and regional and
company/sector news — from free web sources and market data.

## Stack

- Kotlin 2.4 / JVM 25
- Gradle 9 (Kotlin DSL, wrapper included)
- Ktor 3 (Netty server + HTTP client) + kotlinx.serialization

## Requirements

- JDK 25 (Temurin recommended) — the Gradle wrapper handles the rest.

See [`docs/PRD.md`](docs/PRD.md) for the product spec,
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the design, and
[`docs/GLOSSARY.md`](docs/GLOSSARY.md) for domain vocabulary.

## Run

```bash
./gradlew run
# then: curl http://localhost:8080/health
```

`PORT` overrides the default port 8080:

```bash
PORT=9000 ./gradlew run
```

## Configuration

Non-sensitive runtime settings for the extractor live in
[`config/application.yml`](config/application.yml): the provider `baseUrl` and
`model`, the environment variable holding the API key (`apiKeyEnv`), the request
timeout, and the retry count. Point `MARKET_BRIEF_CONFIG` at another file to
override the location; the LLM section selects any OpenAI-compatible provider.
The `run` server still exposes only `/health`; adapter wiring — and therefore
startup config validation — lands with M5.

The API key is a secret and never lives in the repository. It is injected from
1Password at runtime:

```bash
cp config/secrets.env.example config/secrets.env   # then edit the op:// reference
op run --env-file=config/secrets.env -- ./gradlew run
```

`config/secrets.env` holds only 1Password references (`op://vault/item/field`),
not the secret itself, and is gitignored. Any other injection mechanism works
too — the app reads whatever variable `llm.apiKeyEnv` names.

Manual verification against the configured provider (opt-in live test):

```bash
op run --env-file=config/secrets.env -- ./gradlew liveTest
```

## Test

```bash
./gradlew test
```

Live tests (tagged `live`) call the configured LLM provider, so they are
excluded from `test` and `build`. Run them explicitly with credentials:

```bash
op run --env-file=config/secrets.env -- ./gradlew liveTest
```

## Lint

`ktlint` runs as part of `./gradlew build` — a style violation fails the
build. To check or fix without a full build:

```bash
./gradlew ktlintCheck
./gradlew ktlintFormat
```

Style is configured in [`.editorconfig`](.editorconfig).
