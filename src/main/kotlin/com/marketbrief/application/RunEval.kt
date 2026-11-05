package com.marketbrief.application

import com.marketbrief.domain.Article
import com.marketbrief.domain.ClaimSupport
import com.marketbrief.domain.ClaimSupportJudge
import com.marketbrief.domain.DayWindow
import com.marketbrief.domain.EvalReport
import com.marketbrief.domain.EvalStore
import com.marketbrief.domain.ExtractionUsage
import com.marketbrief.domain.GoldSet
import com.marketbrief.domain.NewsItem
import com.marketbrief.domain.NewsItemExtraction
import com.marketbrief.domain.NewsItemExtractor
import com.marketbrief.domain.NewsSource
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RunEval(
    private val newsSource: NewsSource,
    private val extractor: NewsItemExtractor,
    private val evalStore: EvalStore,
    private val goldSet: GoldSet,
    private val claimSupportJudge: ClaimSupportJudge,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun run(window: DayWindow = DayWindow.forReportDate(LocalDate.now(clock))): EvalReport {
        val goldArticleIds = goldSet.cases.mapTo(mutableSetOf()) { it.articleId }
        val articlesById = newsSource.fetch(window).filter { it.id in goldArticleIds }.associateBy { it.id }
        check(articlesById.isNotEmpty()) { "No Gold set Articles matched the NewsSource result" }
        val missingArticleIds = goldArticleIds - articlesById.keys
        check(missingArticleIds.isEmpty()) {
            "NewsSource is missing Gold set Articles: ${missingArticleIds.sorted().joinToString(", ")}"
        }
        val articles = goldSet.cases.map { articlesById.getValue(it.articleId) }
        val results = articles.map { article -> article to extractor.extract(article) }
        val predicted = results.flatMap { it.second.items }
        val expected = results.flatMap { goldSet.expectedFor(it.first.id) }
        val exactMatches = results.sumOf { (article, items) ->
            countOneToOneExactMatches(items.items, goldSet.expectedFor(article.id))
        }
        val exactPrecision = ratio(exactMatches, predicted.size)
        val exactRecall = ratio(exactMatches, expected.size)
        val fieldNames = FIELD_COUNT
        val fieldMatches = results.sumOf { (article, items) ->
            countOneToOneFieldMatches(items.items, goldSet.expectedFor(article.id))
        }
        val fieldPrecision = ratio(fieldMatches, predicted.size * fieldNames)
        val fieldRecall = ratio(fieldMatches, expected.size * fieldNames)
        val citationMetrics = evaluateCitations(results)
        val missing = (expected.size - exactMatches).coerceAtLeast(0)
        val metrics = mapOf(
            "accuracy.precision" to fieldPrecision,
            "accuracy.recall" to fieldRecall,
            "precision" to exactPrecision,
            "recall" to exactRecall,
            "citationQuotePresence" to ratio(citationMetrics.quotePresent, predicted.size),
            "unsupportedClaims" to ratio(citationMetrics.unsupported, predicted.size),
            "supportedClaims" to ratio(citationMetrics.supported, predicted.size),
            "indeterminateClaims" to ratio(citationMetrics.indeterminate, predicted.size),
            "missingValues" to ratio(missing, expected.size),
            "predictedItems" to predicted.size.toDouble(),
            "expectedItems" to expected.size.toDouble(),
            "unsupportedCount" to citationMetrics.unsupported.toDouble(),
            "missingCount" to missing.toDouble(),
        )
        val usageMetrics = extractionUsageMetrics(
            results.map { it.second.usage } + citationMetrics.judgeUsages,
            predicted.size,
        )
        val completeMetrics = metrics + usageMetrics
        val report = EvalReport(
            UUID.randomUUID().toString(),
            Instant.now(clock),
            completeMetrics,
            markdown = markdown(completeMetrics, citationMetrics.reviewSample),
        )
        evalStore.save(report)
        return report
    }

    private fun Article.contains(quote: String): Boolean = normalize(body).contains(normalize(quote)) ||
        normalize(headline).contains(normalize(quote))

    private fun NewsItem.sameClaimAs(other: NewsItem): Boolean = type == other.type && region == other.region &&
        subjects == other.subjects && assertion == other.assertion && direction == other.direction &&
        cause == other.cause && metrics == other.metrics

    private fun NewsItem.fieldMatches(other: NewsItem): Int = listOf(
        type == other.type,
        region == other.region,
        subjects == other.subjects,
        assertion == other.assertion,
        direction == other.direction,
        cause == other.cause,
        metrics == other.metrics,
    ).count { it }

    private fun countOneToOneExactMatches(predicted: List<NewsItem>, expected: List<NewsItem>): Int {
        val unmatched = expected.toMutableList()
        return predicted.count { item ->
            val index = unmatched.indexOfFirst { expectedItem -> item.sameClaimAs(expectedItem) }
            if (index == -1) {
                false
            } else {
                unmatched.removeAt(index)
                true
            }
        }
    }

    private fun countOneToOneFieldMatches(predicted: List<NewsItem>, expected: List<NewsItem>): Int {
        if (predicted.isEmpty() || expected.isEmpty()) return 0
        val weights = Array(predicted.size) { predictedIndex ->
            IntArray(expected.size + predicted.size) { expectedIndex ->
                if (expectedIndex >= expected.size) {
                    0
                } else {
                    val predictedItem = predicted[predictedIndex]
                    val expectedItem = expected[expectedIndex]
                    if (predictedItem.subjects.none { it in expectedItem.subjects }) {
                        0
                    } else {
                        predictedItem.fieldMatches(expectedItem)
                    }
                }
            }
        }
        val assignments = maximumWeightAssignment(weights)
        return assignments.indices.sumOf { predictedIndex ->
            val expectedIndex = assignments[predictedIndex]
            if (expectedIndex in expected.indices) weights[predictedIndex][expectedIndex] else 0
        }
    }

    private fun maximumWeightAssignment(weights: Array<IntArray>): IntArray {
        val rows = weights.size
        val columns = weights.first().size
        val rowPotentials = IntArray(rows + 1)
        val columnPotentials = IntArray(columns + 1)
        val assignedRow = IntArray(columns + 1)
        val previousColumn = IntArray(columns + 1)

        for (row in 1..rows) {
            assignedRow[0] = row
            var currentColumn = 0
            val minimumReducedCost = IntArray(columns + 1) { Int.MAX_VALUE }
            val used = BooleanArray(columns + 1)
            do {
                used[currentColumn] = true
                val currentRow = assignedRow[currentColumn]
                var delta = Int.MAX_VALUE
                var nextColumn = 0
                for (column in 1..columns) {
                    if (used[column]) continue
                    val cost = FIELD_COUNT - weights[currentRow - 1][column - 1]
                    val reducedCost = cost - rowPotentials[currentRow] - columnPotentials[column]
                    if (reducedCost < minimumReducedCost[column]) {
                        minimumReducedCost[column] = reducedCost
                        previousColumn[column] = currentColumn
                    }
                    if (minimumReducedCost[column] < delta) {
                        delta = minimumReducedCost[column]
                        nextColumn = column
                    }
                }
                for (column in 0..columns) {
                    if (used[column]) {
                        rowPotentials[assignedRow[column]] += delta
                        columnPotentials[column] -= delta
                    } else if (column > 0) {
                        minimumReducedCost[column] -= delta
                    }
                }
                currentColumn = nextColumn
            } while (assignedRow[currentColumn] != 0)

            do {
                val previous = previousColumn[currentColumn]
                assignedRow[currentColumn] = assignedRow[previous]
                currentColumn = previous
            } while (currentColumn != 0)
        }

        return IntArray(rows) { -1 }.also { assignments ->
            for (column in 1..columns) {
                val row = assignedRow[column]
                if (row > 0) assignments[row - 1] = column - 1
            }
        }
    }

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator

    private suspend fun evaluateCitations(results: List<Pair<Article, NewsItemExtraction>>): CitationMetrics {
        var quotePresent = 0
        var unsupported = 0
        var supported = 0
        var indeterminate = 0
        val judgeUsages = mutableListOf<ExtractionUsage?>()
        val reviewSamples = mutableListOf<CitationReviewSample>()
        results.forEach { (article, extraction) ->
            extraction.items.forEach { item ->
                val quotesPresent = item.source.quotes.all { quote -> article.contains(quote) }
                if (!quotesPresent) {
                    unsupported++
                    reviewSamples += CitationReviewSample(
                        article.id,
                        item.assertion,
                        item.source.quotes,
                        ClaimSupport.UNSUPPORTED,
                    )
                } else {
                    quotePresent++
                    val judgment = claimSupportJudge.judge(item)
                    judgeUsages += judgment.usage
                    reviewSamples += CitationReviewSample(
                        article.id,
                        item.assertion,
                        item.source.quotes,
                        judgment.support,
                    )
                    when (judgment.support) {
                        ClaimSupport.SUPPORTED -> supported++
                        ClaimSupport.UNSUPPORTED -> unsupported++
                        ClaimSupport.INDETERMINATE -> indeterminate++
                    }
                }
            }
        }
        val manualSample = reviewSamples.sortedWith(compareBy({ it.articleId }, { it.assertion }))
            .take(MANUAL_REVIEW_SAMPLE_SIZE)
        return CitationMetrics(quotePresent, unsupported, supported, indeterminate, judgeUsages, manualSample)
    }

    private fun extractionUsageMetrics(usages: List<ExtractionUsage?>, itemCount: Int): Map<String, Double> {
        val knownUsages = usages.filterNotNull()
        val inputTokens = knownUsages.mapNotNull { it.inputTokens }.sum()
        val outputTokens = knownUsages.mapNotNull { it.outputTokens }.sum()
        val costs = usages.map { it?.estimatedCostUsd }
        val costIsAvailable = costs.isNotEmpty() && costs.all { it != null }
        return buildMap {
            put("usage.inputTokens", inputTokens.toDouble())
            put("usage.outputTokens", outputTokens.toDouble())
            put("usage.calls", usages.size.toDouble())
            put(
                "usage.callsWithTokens",
                knownUsages.count {
                    it.inputTokens != null && it.outputTokens != null
                }.toDouble(),
            )
            put("cost.available", if (costIsAvailable) 1.0 else 0.0)
            if (costIsAvailable) {
                val totalCost = costs.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add)
                put("cost.estimatedUsd", totalCost.toDouble())
                if (itemCount > 0) put("cost.estimatedUsdPerNewsItem", totalCost.toDouble() / itemCount)
            }
        }
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun markdown(metrics: Map<String, Double>, citationReview: List<CitationReviewSample>): String =
        buildString {
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

    private data class CitationMetrics(
        val quotePresent: Int,
        val unsupported: Int,
        val supported: Int,
        val indeterminate: Int,
        val judgeUsages: List<ExtractionUsage?>,
        val reviewSample: List<CitationReviewSample>,
    )

    private data class CitationReviewSample(
        val articleId: String,
        val assertion: String,
        val quotes: List<String>,
        val support: ClaimSupport,
    )

    private companion object {
        const val FIELD_COUNT = 7
        const val MANUAL_REVIEW_SAMPLE_SIZE = 5
    }
}
