# AGENTS.md — market-brief

## Documents

- `docs/PRD.md` — requirements, scope, eval loop. Reach here for *what/why*
  questions.
- `docs/ARCHITECTURE.md` — layers, ports & adapters, seams. Follow it for any
  code change: dependency direction, pure domain, two adapters per port.
- `docs/GLOSSARY.md` — canonical domain vocabulary. Use its terms in code;
  avoid its listed synonyms.
- `MILESTONES.md` — implementation plan; work it in order.

When your change alters what a doc describes, update that doc.

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

Comments must preserve information the code cannot express: a non-obvious
constraint, rationale, or gotcha. KDoc is justified for a caller-visible
contract that the signature cannot express, such as failure semantics. Do not
paraphrase names, narrate implementation steps, or repeat the docs above.

Before finishing, review every comment you added or touched and delete any that
fail this test. Prefer a clearer name or assertion when code can carry the
intent.
