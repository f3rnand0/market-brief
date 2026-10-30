package com.marketbrief.application

import com.marketbrief.domain.Article
import com.marketbrief.domain.DayWindow
import com.marketbrief.domain.EvalReport
import com.marketbrief.domain.EvalStore
import com.marketbrief.domain.GoldSet
import com.marketbrief.domain.NewsItem
import com.marketbrief.domain.NewsItemExtractor
import com.marketbrief.domain.NewsSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RunEval(
    private val newsSource: NewsSource,
    private val extractor: NewsItemExtractor,
    private val evalStore: EvalStore,
    private val goldSet: GoldSet,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun run(window: DayWindow = DayWindow.forReportDate(LocalDate.now(clock))): EvalReport {
        val articles = newsSource.fetch(window).filter {
            it.id in
                goldSet.cases.mapTo(mutableSetOf()) { case -> case.articleId }
        }
        val results = articles.map { article -> article to extractor.extract(article) }
        val predicted = results.flatMap { it.second }
        val expected = results.flatMap { goldSet.expectedFor(it.first.id) }
        val exactMatches = predicted.count { candidate -> expected.any { it.sameClaimAs(candidate) } }
        val exactPrecision = ratio(exactMatches, predicted.size)
        val exactRecall = ratio(exactMatches, expected.size)
        val fieldNames = 7
        val fieldMatches = results.sumOf { (article, items) ->
            items.sumOf { item ->
                goldSet.expectedFor(article.id).maxOfOrNull { expectedItem -> item.fieldMatches(expectedItem) }
                    ?: 0
            }
        }
        val fieldPrecision = ratio(fieldMatches, predicted.size * fieldNames)
        val fieldRecall = ratio(fieldMatches, expected.size * fieldNames)
        val unsupported = results.sumOf { (article, items) ->
            items.count { item -> !item.source.quotes.all { quote -> article.contains(quote) } }
        }
        val missing = (expected.size - exactMatches).coerceAtLeast(0)
        val metrics = mapOf(
            "accuracy.precision" to fieldPrecision,
            "accuracy.recall" to fieldRecall,
            "precision" to exactPrecision,
            "recall" to exactRecall,
            "unsupportedClaims" to ratio(unsupported, predicted.size),
            "missingValues" to ratio(missing, expected.size),
            "cost" to 0.0,
            "predictedItems" to predicted.size.toDouble(),
            "expectedItems" to expected.size.toDouble(),
            "unsupportedCount" to unsupported.toDouble(),
            "missingCount" to missing.toDouble(),
        )
        val report = EvalReport(UUID.randomUUID().toString(), Instant.now(clock), metrics, markdown = markdown(metrics))
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

    private fun ratio(numerator: Int, denominator: Int): Double = if (denominator ==
        0
    ) {
        1.0
    } else {
        numerator.toDouble() / denominator
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun markdown(metrics: Map<String, Double>): String = buildString {
        appendLine("# Extraction evaluation")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|---|---:|")
        metrics.forEach { (name, value) -> appendLine("| $name | ${"%.4f".format(value)} |") }
    }
}
