# market-brief

A Kotlin service that generates a daily summary of the most important global
investment-market news — index moves, an asset-class snapshot, and regional and
company/sector news — from free web sources and market data.

> **Status: skeleton.** This repo currently contains only the project
> scaffolding and the target architecture. No extraction logic is implemented
> yet, and LangChain4j is intentionally not included.

## Stack

- Kotlin 2.4 / JVM 21
- Gradle (Kotlin DSL)
- Ktor 3 (Netty) + kotlinx.serialization

## Layout

```
src/main/kotlin/com/marketbrief/
├── Application.kt       # entry point (boots Ktor, /health)
├── domain/              # pure domain model, ports, services
├── application/         # use cases
├── infrastructure/      # adapters (market data, news ingestion, LLM extractor)
└── http/                # Ktor routes + DTOs
```

See [`PRD.md`](PRD.md) for the product spec and [`GLOSSARY.md`](GLOSSARY.md) for
domain vocabulary.

## Run

```bash
./gradlew run
# then: curl http://localhost:8080/health
```

## Test

```bash
./gradlew test
```
