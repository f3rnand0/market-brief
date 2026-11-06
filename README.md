# market-brief

A Kotlin service that generates a daily summary of the most important global
investment-market news — index moves, an asset-class snapshot, and regional and
company/sector news — from free web sources and market data.

## Requirements

- JDK 25 (Temurin recommended) — the Gradle wrapper handles the rest.

See [`docs/PRD.md`](docs/PRD.md) for the product spec,
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the design, and
[`docs/GLOSSARY.md`](docs/GLOSSARY.md) for domain vocabulary.

## Run

Set the API key variable named by `llm.apiKeyEnv` in
[`config/application.yml`](config/application.yml) before starting the server;
see [Configuration](#configuration).

```bash
./gradlew run
# then: curl http://localhost:8080/health
```

`PORT` overrides the default port 8080:

```bash
PORT=9000 ./gradlew run
```

The server reads configured RSS feeds and GDELT, fetches market data, and uses
the configured LLM provider. The packaged corpus is used for evaluation, not
live report generation. Generate and retrieve a report:

```bash
curl -X POST http://localhost:8080/report/generate
# The response contains the report date. Substitute it in either URL:
curl http://localhost:8080/report/2026-10-28
curl http://localhost:8080/report/2026-10-28.html
```

The endpoint uses the current report date and covers the previous calendar day.
The scheduler also runs daily at 09:00 Europe/Berlin. JSON reports are saved
under `storage.reportsDir` (default `data/reports`) as `<date>.json`; the HTML
endpoint renders the saved report in its response. To save the HTML locally:

```bash
curl http://localhost:8080/report/2026-10-28.html \
  --output 2026-10-28.html
```

See [the architecture guide](docs/ARCHITECTURE.md#24-section-assembly-rules)
for report selection and source-failure behavior.

## Configuration

Runtime settings for the LLM live in
[`config/application.yml`](config/application.yml). Set
`MARKET_BRIEF_CONFIG` to load a different file. Report extraction and citation
evaluation use the same configured model. The server validates its LLM
configuration at startup.

The API key is a secret and never lives in the repository. It is injected from
1Password at runtime:

```bash
cp config/secrets.env.example config/secrets.env   # then edit the op:// reference
op run --env-file=config/secrets.env -- ./gradlew run
```

`config/secrets.env` holds only 1Password references (`op://vault/item/field`)
and is gitignored. Other secret injectors can set the environment variable
named by `llm.apiKeyEnv`.

## Test

```bash
./gradlew test
```

Live tests (tagged `live`) call the configured LLM provider, so they are
excluded from `test` and `build`. Run them explicitly with credentials:

```bash
op run --env-file=config/secrets.env -- ./gradlew liveTest
```

## Checks

Run the local quality checks with:

```bash
./gradlew check
```

The tracked Git hook can run ktlint when creating a merge commit. Enable it once
per clone:

```bash
git config core.hooksPath .githooks
```

The hook runs `./gradlew ktlintCheck`.
