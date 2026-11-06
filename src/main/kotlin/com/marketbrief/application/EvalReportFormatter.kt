package com.marketbrief.application

internal class EvalReportFormatter {
    fun format(metrics: Map<String, Double>, citationReview: List<CitationReviewSample>): String = buildString {
        appendLine("# Extraction evaluation")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|---|---:|")
        metrics.forEach { (name, value) -> appendLine("| $name | ${"%.4f".format(value)} |") }
        appendLine()
        appendLine("## Citation review sample")
        if (citationReview.isEmpty()) {
            appendLine("No predicted NewsItems to review.")
        } else {
            citationReview.forEach { sample ->
                appendLine("- ${sample.articleId}: ${sample.support}")
                appendLine("  - Assertion: ${sample.assertion.replace("|", "\\|")}")
                sample.quotes.forEach { quote -> appendLine("  - Source quote: ${quote.replace("|", "\\|")}") }
            }
        }
    }
}
