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
            "Exact scores require every NewsItem field to match. Normalized exact matches allow case and punctuation changes, but still require all fields to agree.",
        )
        appendLine(
            "Claim alignment uses one-to-one Article-level pairs with similar assertion or subject wording and no conflicting direction or metrics. These are review candidates, not confirmed matches.",
        )
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|---|---:|")
        metrics.forEach { (name, value) -> appendLine("| $name | ${"%.4f".format(value)} |") }
        appendLine()
        appendLine("## Candidate claim alignments for review")
        if (itemMetrics.claimAlignments.isEmpty()) appendLine("None.")
        itemMetrics.claimAlignments.forEach { candidate ->
            val alignmentKind = if (candidate.differingFields.isEmpty()) "exact" else "candidate"
            appendLine(
                "- ${candidate.articleId}: $alignmentKind " +
                    "(assertion similarity ${"%.2f".format(candidate.assertionSimilarity)})",
            )
            appendLine(
                "  - Predicted: ${candidate.prediction.type} / " +
                    "${candidate.prediction.subjects.joinToString { it.name }} — ${candidate.prediction.assertion}",
            )
            appendLine(
                "  - Gold: ${candidate.expected.type} / " +
                    "${candidate.expected.subjects.joinToString { it.name }} — ${candidate.expected.assertion}",
            )
            appendLine("  - Different fields: ${candidate.differingFields.ifEmpty { listOf("none") }.joinToString()}")
        }
        appendLine()
        appendLine("## Unaligned predictions")
        if (itemMetrics.unalignedPredictions.isEmpty()) appendLine("None.")
        itemMetrics.unalignedPredictions.forEach { (articleId, item) ->
            appendLine("- $articleId: ${item.type} / ${item.subjects.joinToString { it.name }} — ${item.assertion}")
        }
        appendLine()
        appendLine("## Unaligned Gold items")
        if (itemMetrics.unalignedExpected.isEmpty()) appendLine("None.")
        itemMetrics.unalignedExpected.forEach { (articleId, item) ->
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
