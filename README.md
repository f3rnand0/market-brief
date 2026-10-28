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

## Test

```bash
./gradlew test
```

## Lint

`ktlint` runs as part of `./gradlew build` — a style violation fails the
build. To check or fix without a full build:

```bash
./gradlew ktlintCheck
./gradlew ktlintFormat
```

Style is configured in [`.editorconfig`](.editorconfig).
