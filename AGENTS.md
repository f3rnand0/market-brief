# AGENTS.md — market-brief

## Documents

- `docs/PRD.md` — product goals and constraints.
- `docs/ARCHITECTURE.md` — implemented layers, ports, and runtime behavior;
  follow it for code changes.
- `docs/GLOSSARY.md` — canonical domain vocabulary. Use its terms in code.

Keep current behavior distinct from product goals and unfinished work. When a
change alters what a doc describes, update that doc.

## Build gate

`./gradlew build` runs `ktlintCheck`, so a style violation fails the build.
Fix with `./gradlew ktlintFormat` rather than hand-wrapping. Style lives in
`.editorconfig` (`ktlint_code_style = intellij_idea`, 120 columns); do not
reformat unrelated files to satisfy it.

## Version pinning (upgrade rules)

The couplings no config file confesses:

- **Gradle ↔ Kotlin plugin**: the Kotlin plugin sets a minimum Gradle version.
  Upgrade the two together against the Kotlin–Gradle compatibility matrix.
  Verified combination: Gradle 9.8.2 + Kotlin 2.4.21 on JVM 25.
- **`kotlinx-serialization-json`**: coupled to the Kotlin compiler; bump in
  lockstep with the Kotlin plugin.
- **LangChain4j**: align all `langchain4j*` modules via the `langchain4j-bom`.
  Verified: 1.23.0 (`langchain4j-core` + `langchain4j-open-ai`).
- **kaml**: YAML decoding for runtime config. Verified: 0.104.0 with
  kotlinx-serialization 1.12.0.
- **Ktor**: keep every module on the single `ktorVersion` constant.
- **Direct dependencies**: declare the libraries the source imports directly
  (coroutines, SLF4J) instead of relying on transitive versions. Verified:
  `kotlinx-coroutines-core` 1.11.0, `slf4j-api` 2.0.19.
- **ktlint**: the `1.8.0` engine parses with its own bundled Kotlin compiler,
  not the project's. A Kotlin language upgrade that the engine can't parse
  fails the build with a parse error, not a style error — bump ktlint with the
  Kotlin plugin.

## Naming

Class and interface names are noun phrases. A collaborator that performs an
action takes an agent noun — `MarketDataFetcher`, `JsonReportStore`,
`RssNewsSource` — and the verb belongs to its method: `snapshot()`, `fetch()`,
`extract()`. Use cases are the one imperative: `GenerateDailyReport` names the
step the agent takes.

`*Data` names a record (a Kotlin `data class`), not a collaborator.

## Comments

Do not add comments or KDoc by default. Names, types, and code should explain
what the code does.

Add a comment only when it conveys information that cannot be made clear through
a better name, type, or code structure, such as a non-obvious constraint,
rationale, gotcha, or caller-visible contract.

Do not explain what a variable, property, method, or class does when its name
and implementation make that clear. Before finishing, review every comment you
added or touched and remove any that does not meet this standard.
