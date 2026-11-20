package com.marketbrief.application

import com.marketbrief.domain.ClaimSupportJudge
import com.marketbrief.domain.DayWindow
import com.marketbrief.domain.EvalCheckpoint
import com.marketbrief.domain.EvalCheckpointStore
import com.marketbrief.domain.EvalReport
import com.marketbrief.domain.EvalStore
import com.marketbrief.domain.ExtractionUsage
import com.marketbrief.domain.GoldSet
import com.marketbrief.domain.NewsItemExtractor
import com.marketbrief.domain.NewsSource
import org.slf4j.LoggerFactory
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
    claimSupportJudge: ClaimSupportJudge,
    private val clock: Clock = Clock.systemUTC(),
    private val checkpointStore: EvalCheckpointStore? = null,
) {
    private val itemMetricsEvaluator = NewsItemMetricsEvaluator()
    private val citationMetricsEvaluator = CitationMetricsEvaluator(claimSupportJudge)
    private val reportFormatter = EvalReportFormatter()

    suspend fun run(window: DayWindow = DayWindow.forReportDate(LocalDate.now(clock))): EvalReport {
        val evalStartedAt = System.nanoTime()
        val goldArticleIds = goldSet.cases.mapTo(mutableSetOf()) { it.articleId }
        val articlesById = newsSource.fetch(window).filter { it.id in goldArticleIds }.associateBy { it.id }
        check(articlesById.isNotEmpty()) { "No Gold set Articles matched the NewsSource result" }
        val missingArticleIds = goldArticleIds - articlesById.keys
        check(missingArticleIds.isEmpty()) {
            "NewsSource is missing Gold set Articles: ${missingArticleIds.sorted().joinToString(", ")}"
        }
        val articles = goldSet.cases.map { articlesById.getValue(it.articleId) }
        val articleIds = articles.map { it.id }
        val savedCheckpoint = checkpointStore?.load()?.takeIf {
            it.goldSetVersion == goldSet.version && it.articleIds == articleIds
        }
        val extractions = savedCheckpoint?.extractions?.toMutableMap() ?: mutableMapOf()
        log.info("Starting eval for Gold set {} with {} Articles", goldSet.version, articles.size)
        val results = articles.mapIndexed { index, article ->
            val savedExtraction = extractions[article.id]
            if (savedExtraction != null) {
                log.info("Resuming Article {}/{} from checkpoint: {}", index + 1, articles.size, article.id)
                return@mapIndexed article to savedExtraction
            }
            val startedAt = System.nanoTime()
            log.info("Extracting Article {}/{}: {}", index + 1, articles.size, article.id)
            val extraction = extractor.extract(article)
            log.info(
                "Extracted Article {}/{}: {} items={} elapsedMs={}",
                index + 1,
                articles.size,
                article.id,
                extraction.items.size,
                elapsedMillis(startedAt),
            )
            extractions[article.id] = extraction
            checkpointStore?.save(EvalCheckpoint(goldSet.version, articleIds, extractions.toMap()))
            article to extraction
        }
        log.info("Starting citation checks for {} extracted NewsItems", results.sumOf { it.second.items.size })
        val itemMetrics = itemMetricsEvaluator.evaluate(results, goldSet)
        val citationMetrics = citationMetricsEvaluator.evaluate(results)
        val usages = results.map { it.second.usage } + citationMetrics.judgeUsages
        val completeMetrics = itemMetrics.metrics + citationMetrics.metrics +
            extractionUsageMetrics(usages, itemMetrics.predictedCount)
        val report = EvalReport(
            UUID.randomUUID().toString(),
            Instant.now(clock),
            completeMetrics,
            markdown = reportFormatter.format(
                completeMetrics,
                citationMetrics.reviewSamples,
                itemMetrics,
                goldSet.version,
                articleIds,
            ),
            goldSetVersion = goldSet.version,
            articleIds = articleIds,
        )
        evalStore.save(report)
        checkpointStore?.clear()
        log.info(
            "Completed eval {} for Gold set {} Articles={} elapsedMetrics={}",
            report.id,
            goldSet.version,
            articles.size,
            elapsedMillis(evalStartedAt),
        )
        return report
    }

    private fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / NANOS_PER_MILLI

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

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000
        val log = LoggerFactory.getLogger(RunEval::class.java)
    }
}
