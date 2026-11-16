package com.marketbrief.application

import com.marketbrief.domain.Article
import com.marketbrief.domain.ClaimSupport
import com.marketbrief.domain.ClaimSupportContext
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
                val quotesPresent = item.source.quotes.all(article::containsQuote)
                if (!quotesPresent) {
                    unsupported++
                    reviewSamples += CitationReviewSample(
                        article.id,
                        item.assertion,
                        item.source.quotes,
                        ClaimSupport.UNSUPPORTED,
                        article.headline,
                        relevantContext(article, item.source.quotes),
                        false,
                    )
                } else {
                    quotePresent++
                    val judgment = claimSupportJudge.judge(
                        item,
                        ClaimSupportContext(article.headline, relevantContext(article, item.source.quotes)),
                    )
                    judgeUsages += judgment.usage
                    reviewSamples += CitationReviewSample(
                        article.id,
                        item.assertion,
                        item.source.quotes,
                        judgment.support,
                        article.headline,
                        relevantContext(article, item.source.quotes),
                        true,
                    )
                    when (judgment.support) {
                        ClaimSupport.SUPPORTED -> supported++
                        ClaimSupport.UNSUPPORTED -> unsupported++
                        ClaimSupport.INDETERMINATE -> indeterminate++
                    }
                }
            }
        }
        val manualSample = ClaimSupport.entries.flatMap { outcome ->
            reviewSamples.filter { it.support == outcome }.sortedWith(compareBy({ it.articleId }, { it.assertion }))
                .take(MANUAL_REVIEW_PER_OUTCOME)
        } + reviewSamples.filter { sample ->
            sample.quotes.any { quote ->
                results.first { it.first.id == sample.articleId }.first.containsQuote(quote).not()
            }
        }.sortedWith(compareBy({ it.articleId }, { it.assertion })).take(MANUAL_REVIEW_PER_OUTCOME)
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

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator

    private companion object {
        const val MANUAL_REVIEW_PER_OUTCOME = 2
        const val CONTEXT_CHARS = 350
        const val MAX_CONTEXT_CHARS = 1_200
    }

    private fun relevantContext(article: Article, quotes: List<String>): String {
        val text = article.body
        val windows = quotes.mapNotNull { quote ->
            val index = text.indexOf(quote, ignoreCase = true)
            if (index < 0) {
                null
            } else {
                text.substring(
                    (index - CONTEXT_CHARS).coerceAtLeast(0),
                    (index + quote.length + CONTEXT_CHARS).coerceAtMost(text.length),
                )
            }
        }
        return windows.distinct().joinToString("\n…\n").ifBlank { text.take(MAX_CONTEXT_CHARS) }
            .take(MAX_CONTEXT_CHARS)
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
    val headline: String = "",
    val context: String = "",
    val quotesPresent: Boolean = true,
)
