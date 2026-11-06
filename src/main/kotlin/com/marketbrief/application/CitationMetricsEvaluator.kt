package com.marketbrief.application

import com.marketbrief.domain.Article
import com.marketbrief.domain.ClaimSupport
import com.marketbrief.domain.ClaimSupportJudge
import com.marketbrief.domain.ExtractionUsage
import com.marketbrief.domain.NewsItemExtraction

internal class CitationMetricsEvaluator(private val claimSupportJudge: ClaimSupportJudge) {
    suspend fun evaluate(results: List<Pair<Article, NewsItemExtraction>>): CitationMetrics {
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
        return CitationMetrics(
            metrics = mapOf(
                "citationQuotePresence" to ratio(quotePresent, results.sumOf { it.second.items.size }),
                "unsupportedClaims" to ratio(unsupported, results.sumOf { it.second.items.size }),
                "supportedClaims" to ratio(supported, results.sumOf { it.second.items.size }),
                "indeterminateClaims" to ratio(indeterminate, results.sumOf { it.second.items.size }),
                "unsupportedCount" to unsupported.toDouble(),
            ),
            judgeUsages = judgeUsages,
            reviewSamples = manualSample,
        )
    }

    private fun Article.contains(quote: String): Boolean = normalize(body).contains(normalize(quote)) ||
        normalize(headline).contains(normalize(quote))

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator

    private companion object {
        const val MANUAL_REVIEW_SAMPLE_SIZE = 5
    }
}

internal data class CitationMetrics(
    val metrics: Map<String, Double>,
    val judgeUsages: List<ExtractionUsage?>,
    val reviewSamples: List<CitationReviewSample>,
)

internal data class CitationReviewSample(
    val articleId: String,
    val assertion: String,
    val quotes: List<String>,
    val support: ClaimSupport,
)
