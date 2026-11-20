# Evaluation history

## 2026-11-20 — Eval report

### Inputs and models

| Input | Value |
| --- | --- |
| Gold set | `gold-1` |
| Articles | 32 (`article-001` through `article-032`) |
| Extraction model | `deepseek-v4.1-flash` |
| Claim-support judge | `minimax-m3` |
| Duration | About 39 minutes |
| Estimated cost | 0.14624565 USD |
| Full report | [JSON](../data/evals/8c077a67-2494-42f4-843e-8ecd8c4874e8.json) · [Markdown](../data/evals/8c077a67-2494-42f4-843e-8ecd8c4874e8.md) |

### Results

| Extraction model | Predicted items | Expected items | Exact precision / recall | Normalized exact precision / recall | Field precision / recall | Candidate alignment precision / recall | Missing values / count | Quote presence | Supported / indeterminate / unsupported claims | Estimated cost (USD) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `deepseek-v4.1-flash` | 208 | 32 | 0.000 / 0.000 | 0.000 / 0.000 | 0.068 / 0.442 | 0.101 / 0.656 | 1.000 / 32 | 1.000 | 0.745 / 0.255 / 0.000 | 0.14624565 |

No exact Gold items matched. Normalized-exact scoring also found no matches.
Candidate claim alignment had precision/recall of 0.101 / 0.656. Field-level
precision/recall was 0.068 / 0.442. Citation quotes were present for 100.0% of
predictions. The judge marked 74.5% supported, 25.5% indeterminate, and 0.0%
unsupported. The report recorded 32 missing expected items.

Usage: 155121 input tokens, 168842 output tokens across 240 model calls.

## 2026-11-20 — Gold-set model comparison

### Inputs and models

| Input | Value |
| --- | --- |
| Gold set | `gold-1` |
| Articles | 32 (`article-001` through `article-032`) |
| Extraction models | `deepseek-v4.1-flash`, `glm-5.3-flash` |
| Claim-support judge | `minimax-m3` |
| Duration | About 45 minutes |
| Estimated combined cost | $0.2023471 USD |
| Full report | `build/reports/langchain4j-gold-set.md` |

### Results

| Extraction model | Predicted items | Exact precision / recall | Field precision / recall | Quote presence | Supported / indeterminate claims | Estimated cost (USD) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `deepseek-v4.1-flash` | 202 | 0.000 / 0.000 | 0.069 / 0.433 | 1.000 | 0.698 / 0.302 | 0.14999625 |
| `glm-5.3-flash` | 131 | 0.000 / 0.000 | 0.101 / 0.415 | 1.000 | 0.489 / 0.511 | 0.05235085 |

Neither model exactly matched a Gold item. Field-level scores show partial
agreement; quote presence was 100% for both. This comparison predates candidate
claim-alignment scoring.

