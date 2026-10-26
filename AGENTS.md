# AGENTS.md — market-brief

Kotlin service producing a daily summary of global investment-market news.

## Documents

- `PRD.md` — the *what* (requirements, scope, eval loop).
- `ARCHITECTURE.md` — the *how* (DDD layers, ports & adapters, seams).
- `GLOSSARY.md` — canonical domain vocabulary.

Read the doc matching your task before changing code; update it when your
change alters what it describes.

## Build & test

- `./gradlew build` / `./gradlew test` / `./gradlew run`
- Kotlin 2.4, JVM 25, Gradle wrapper 9.8.

## Version pinning (upgrade rules)

When upgrading toolchain or dependencies, respect these couplings:

- **Gradle ↔ Kotlin plugin**: the Kotlin Gradle plugin sets a minimum Gradle
  version. Upgrade the two together and check the Kotlin–Gradle compatibility
  matrix first. Verified combination: Gradle 9.8.2 + Kotlin 2.4.21 on JVM 25.
  Gradle is pinned via `gradle-wrapper.properties`, Kotlin via the `plugins`
  block — never use dynamic versions.
- **`kotlinx-serialization-json`**: coupled to the Kotlin compiler version;
  bump in lockstep with the Kotlin plugin, never independently.
- **LangChain4j**: pin all `langchain4j*` modules to one version via the
  `langchain4j-bom` so core, OpenAI, and structured-output modules cannot
  drift apart.
- **Ktor**: pinned via the single `ktorVersion` constant in
  `build.gradle.kts`; keep it that way.

## Conventions

- Dependencies point inward: domain → nothing, application → domain,
  adapters → domain, http → application. Only `main` (composition root)
  imports both application and adapters.
- Domain is pure Kotlin: no framework or serialization annotations.
- Every port has a production adapter plus a fallback/fake.
