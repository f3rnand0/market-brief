# Evaluation history

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
