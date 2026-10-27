# AGENTS.md — market-brief

## Documents

- `PRD.md` — requirements, scope, eval loop. Reach here for *what/why*
  questions.
- `ARCHITECTURE.md` — layers, ports & adapters, seams. Follow it for any code
  change: dependency direction, pure domain, two adapters per port.
- `GLOSSARY.md` — canonical domain vocabulary. Use its terms in code; avoid
  its listed synonyms.
- `MILESTONES.md` — implementation plan; work it in order.

When your change alters what a doc describes, update that doc.

## Version pinning (upgrade rules)

The couplings no config file confesses:

- **Gradle ↔ Kotlin plugin**: the Kotlin plugin sets a minimum Gradle version.
  Upgrade the two together against the Kotlin–Gradle compatibility matrix.
  Verified combination: Gradle 9.8.2 + Kotlin 2.4.21 on JVM 25.
- **`kotlinx-serialization-json`**: coupled to the Kotlin compiler; bump in
  lockstep with the Kotlin plugin.
- **LangChain4j**: align all `langchain4j*` modules via the `langchain4j-bom`.
- **Ktor**: keep every module on the single `ktorVersion` constant.

## Naming

Class and interface names are noun phrases. A collaborator that performs an
action takes an agent noun — `MarketDataFetcher`, `JsonReportStore`,
`RssNewsSource` — and the verb belongs to its method: `snapshot()`, `fetch()`,
`extract()`. Use cases are the one imperative: `GenerateDailyReport` names the
step the agent takes.

`*Data` names a record (a Kotlin `data class`), not a collaborator.

## Comments

Comment only what docs and code don't say: the non-obvious decision, the
gotcha. Restatements of the docs above and narration of readable code go
stale — delete them.
