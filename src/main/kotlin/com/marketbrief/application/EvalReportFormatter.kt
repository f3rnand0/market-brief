package com.marketbrief.application

internal class EvalReportFormatter {
    fun format(
        metrics: Map<String, Double>,
        citationReview: List<CitationReviewSample>,
        itemMetrics: ItemMetrics,
        goldVersion: String,
        articleIds: List<String>,
    ): String = buildString {
        appendLine("# Extraction evaluation")
        appendLine()
        appendLine("Gold set: `$goldVersion`")
        appendLine("Articles: ${articleIds.sorted().joinToString(", ")}")
        appendLine()
        appendLine(
            "Exact precision/recall require every NewsItem field to match. Reviewed-match precision/recall normalize assertion case and punctuation while requiring type, region, subjects, direction, cause, and metrics to match.",
        )
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|---|---:|")
        metrics.forEach { (name, value) -> appendLine("| $name | ${"%.4f".format(value)} |") }
        appendLine()
        appendLine("## False positives")
        if (itemMetrics.falsePositives.isEmpty()) appendLine("None.")
        itemMetrics.falsePositives.forEach { (articleId, item) ->
            appendLine("- $articleId: ${item.type} / ${item.subjects.joinToString { it.name }} — ${item.assertion}")
        }
        appendLine()
        appendLine("## False negatives")
        if (itemMetrics.falseNegatives.isEmpty()) appendLine("None.")
        itemMetrics.falseNegatives.forEach { (articleId, item) ->
            appendLine("- $articleId: ${item.type} / ${item.subjects.joinToString { it.name }} — ${item.assertion}")
        }
        appendLine()
        appendLine("## Citation review sample")
        if (citationReview.isEmpty()) {
            appendLine("No predicted NewsItems to review.")
        } else {
            citationReview.forEach { sample ->
                appendLine("- ${sample.articleId}: ${sample.support}")
                appendLine("  - Quote presence: ${if (sample.quotesPresent) "present" else "missing"}")
                appendLine("  - Headline: ${sample.headline}")
                appendLine("  - Assertion: ${sample.assertion.replace("|", "\\|")}")
                appendLine("  - Article context: ${sample.context.replace("|", "\\|")}")
                sample.quotes.forEach { quote -> appendLine("  - Source quote: ${quote.replace("|", "\\|")}") }
            }
        }
    }
}
