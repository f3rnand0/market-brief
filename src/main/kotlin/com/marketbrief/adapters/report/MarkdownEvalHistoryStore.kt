package com.marketbrief.adapters.report

import com.marketbrief.domain.EvalHistoryStore
import com.marketbrief.domain.EvalReport
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.time.Duration
import java.time.ZoneOffset
import java.util.Locale

class MarkdownEvalHistoryStore(
    private val path: Path,
    private val extractionModel: String,
    private val claimSupportJudgeModel: String,
) : EvalHistoryStore {
    override fun append(report: EvalReport, durationMillis: Long) {
        val currentHistory = if (Files.exists(path)) Files.readString(path) else ""
        if (currentHistory.contains(report.id)) return
        path.parent?.let(Files::createDirectories)
        val previousEntries = currentHistory.substringAfter("# Evaluation history", "").trim()
        val contents = buildString {
            appendLine("# Evaluation history")
            appendLine()
            append(format(report, durationMillis))
            if (previousEntries.isNotEmpty()) {
                appendLine()
                append(previousEntries)
                appendLine()
            }
        }
        Files.writeString(path, contents, CREATE, TRUNCATE_EXISTING, WRITE)
    }

    private fun format(report: EvalReport, durationMillis: Long): String = buildString {
        appendLine("## ${report.generatedAt.atZone(ZoneOffset.UTC).toLocalDate()} — Eval report")
        appendLine()
        appendLine("### Inputs and models")
        appendLine()
        appendLine("| Input | Value |")
        appendLine("| --- | --- |")
        appendLine("| Gold set | `${report.goldSetVersion}` |")
        appendLine("| Articles | ${articleRange(report.articleIds)} |")
        appendLine("| Extraction model | `$extractionModel` |")
        appendLine("| Claim-support judge | `$claimSupportJudgeModel` |")
        appendLine("| Duration | About ${Duration.ofMillis(durationMillis).toMinutes()} minutes |")
        appendLine("| Estimated cost | ${report.metrics["cost.estimatedUsd"]?.toString() ?: "unavailable"} USD |")
        appendLine(
            "| Full report | [JSON](../data/evals/${report.id}.json) · " +
                "[Markdown](../data/evals/${report.id}.md) |",
        )
        appendLine()
        appendLine("### Results")
        appendLine()
        appendLine(
            "| Extraction model | Predicted items | Expected items | Exact precision / recall | " +
                "Normalized exact precision / recall | Field precision / recall | " +
                "Candidate alignment precision / recall | Missing values / count | Quote presence | " +
                "Supported / indeterminate / unsupported claims | Estimated cost (USD) |",
        )
        appendLine(
            "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
        )
        appendLine(
            "| `$extractionModel` | ${metric(report, "predictedItems", 0)} | ${metric(report, "expectedItems", 0)} | " +
                "${metric(report, "precision")} / ${metric(report, "recall")} | " +
                "${metric(report, "normalizedExact.precision")} / ${metric(report, "normalizedExact.recall")} | " +
                "${metric(report, "accuracy.precision")} / ${metric(report, "accuracy.recall")} | " +
                "${metric(report, "claimAlignment.candidatePrecision")} / " +
                "${metric(report, "claimAlignment.candidateRecall")} | " +
                "${metric(report, "missingValues")} / ${metric(report, "missingCount", 0)} | " +
                "${metric(report, "citationQuotePresence")} | " +
                "${metric(report, "supportedClaims")} / ${metric(report, "indeterminateClaims")} / " +
                "${metric(report, "unsupportedClaims")} | " +
                "${report.metrics["cost.estimatedUsd"]?.toString() ?: "unavailable"} |",
        )
        appendLine()
        appendLine(interpretation(report))
        appendLine()
        appendLine(
            "Usage: ${metric(report, "usage.inputTokens", 0)} input tokens, " +
                "${metric(report, "usage.outputTokens", 0)} output tokens across " +
                "${metric(report, "usage.calls", 0)} model calls.",
        )
        appendLine()
    }

    private fun articleRange(articleIds: List<String>): String = if (articleIds.isEmpty()) {
        "0"
    } else {
        "${articleIds.size} (`${articleIds.first()}` through `${articleIds.last()}`)"
    }

    private fun metric(report: EvalReport, name: String, decimals: Int = 3): String = report.metrics[name]
        ?.let { String.format(Locale.ROOT, "%.${decimals}f", it) }
        ?: "unavailable"

    private fun interpretation(report: EvalReport): String {
        val sentences = mutableListOf<String>()
        val exactPrecision = report.metrics["precision"]
        val exactRecall = report.metrics["recall"]
        val normalizedPrecision = report.metrics["normalizedExact.precision"]
        val normalizedRecall = report.metrics["normalizedExact.recall"]
        if (exactPrecision == 0.0 && exactRecall == 0.0) sentences += "No exact Gold items matched."
        if (normalizedPrecision == 0.0 && normalizedRecall == 0.0) {
            sentences += "Normalized-exact scoring also found no matches."
        }
        val candidatePrecision = report.metrics["claimAlignment.candidatePrecision"]
        val candidateRecall = report.metrics["claimAlignment.candidateRecall"]
        if (candidatePrecision != null && candidateRecall != null) {
            sentences +=
                "Candidate claim alignment had precision/recall of " +
                "${metric(report, "claimAlignment.candidatePrecision")} / " +
                "${metric(report, "claimAlignment.candidateRecall")}."
        }
        val fieldPrecision = report.metrics["accuracy.precision"]
        val fieldRecall = report.metrics["accuracy.recall"]
        if (fieldPrecision != null && fieldRecall != null) {
            sentences +=
                "Field-level precision/recall was ${metric(report, "accuracy.precision")} / " +
                "${metric(report, "accuracy.recall")}."
        }
        report.metrics["citationQuotePresence"]?.let {
            sentences += "Citation quotes were present for ${percent(it)} of predictions."
        }
        val supported = report.metrics["supportedClaims"]
        val indeterminate = report.metrics["indeterminateClaims"]
        val unsupported = report.metrics["unsupportedClaims"]
        if (supported != null && indeterminate != null && unsupported != null) {
            sentences +=
                "The judge marked ${percent(supported)} supported, ${percent(indeterminate)} indeterminate, " +
                "and ${percent(unsupported)} unsupported."
        }
        val missingCount = report.metrics["missingCount"]
        if (missingCount != null) sentences += "The report recorded ${missingCount.toInt()} missing expected items."
        return sentences.joinToString(" ")
    }

    private fun percent(value: Double): String = "${String.format(Locale.ROOT, "%.1f", value * 100)}%"
}
