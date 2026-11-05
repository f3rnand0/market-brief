package com.marketbrief.application

import com.marketbrief.domain.Article
import com.marketbrief.domain.ClaimSupport
import com.marketbrief.domain.ClaimSupportJudge
import com.marketbrief.domain.ClaimSupportJudgment
import com.marketbrief.domain.DayWindow
import com.marketbrief.domain.Direction
import com.marketbrief.domain.ExtractionUsage
import com.marketbrief.domain.GoldCase
import com.marketbrief.domain.GoldSet
import com.marketbrief.domain.Metric
import com.marketbrief.domain.NewsItem
import com.marketbrief.domain.NewsItemExtraction
import com.marketbrief.domain.NewsItemExtractor
import com.marketbrief.domain.NewsItemType
import com.marketbrief.domain.NewsSource
import com.marketbrief.domain.Region
import com.marketbrief.domain.Source
import com.marketbrief.domain.Subject
import com.marketbrief.fakes.InMemoryEvalStore
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunEvalTest {
    @Test
    fun `duplicate predictions do not match the same expected item more than once`() = runBlocking {
        val article = article()
        val expectedItem = newsItem(article.id)
        val report = runEval(
            articles = listOf(article),
            itemsByArticleId = mapOf(article.id to listOf(expectedItem, expectedItem)),
            goldSet = GoldSet(listOf(GoldCase(article.id, listOf(expectedItem)))),
        ).run(DayWindow(article.publishedAt))

        assertEquals(0.5, report.metrics.getValue("precision"))
        assertEquals(1.0, report.metrics.getValue("recall"))
        assertEquals(0.5, report.metrics.getValue("accuracy.precision"))
        assertEquals(1.0, report.metrics.getValue("accuracy.recall"))
        assertEquals(0.0, report.metrics.getValue("missingValues"))
    }

    @Test
    fun `field matching chooses the one-to-one assignment with the most matching fields`() = runBlocking {
        val article = article()
        val base = newsItem(article.id)
        val firstExpected = base.copy(assertion = "The market fell.")
        val secondExpected = base.copy(
            subjects = listOf(Subject("S&P 500"), Subject("Nasdaq")),
            assertion = "The indexes fell.",
        )
        val firstPrediction = base
        val secondPrediction = base.copy(
            assertion = firstExpected.assertion,
            direction = Direction.UP,
            metrics = listOf(Metric("2", "%")),
        )
        val report = runEval(
            articles = listOf(article),
            itemsByArticleId = mapOf(article.id to listOf(firstPrediction, secondPrediction)),
            goldSet = GoldSet(listOf(GoldCase(article.id, listOf(firstExpected, secondExpected)))),
        ).run(DayWindow(article.publishedAt))

        assertEquals(5.0 / 7.0, report.metrics.getValue("accuracy.precision"))
        assertEquals(5.0 / 7.0, report.metrics.getValue("accuracy.recall"))
    }

    @Test
    fun `an empty extraction does not produce perfect precision`() = runBlocking {
        val article = article()
        val expectedItem = newsItem(article.id)
        val report = runEval(
            articles = listOf(article),
            itemsByArticleId = emptyMap(),
            goldSet = GoldSet(listOf(GoldCase(article.id, listOf(expectedItem)))),
        ).run(DayWindow(article.publishedAt))

        assertEquals(0.0, report.metrics.getValue("precision"))
        assertEquals(0.0, report.metrics.getValue("accuracy.precision"))
        assertEquals(0.0, report.metrics.getValue("recall"))
        assertEquals(1.0, report.metrics.getValue("missingValues"))
        assertEquals(0.0, report.metrics.getValue("unsupportedClaims"))
        assertEquals(0.0, report.metrics.getValue("cost.available"))
        assertFalse(report.metrics.containsKey("cost.estimatedUsd"))
    }

    @Test
    fun `reports estimated token cost per run and per NewsItem`() = runBlocking {
        val first = article("gold-a")
        val second = article("gold-b")
        val firstItem = newsItem(first.id)
        val secondItem = newsItem(second.id)
        val report = runEval(
            articles = listOf(first, second),
            itemsByArticleId = mapOf(first.id to listOf(firstItem), second.id to listOf(secondItem)),
            goldSet = GoldSet(
                listOf(GoldCase(first.id, listOf(firstItem)), GoldCase(second.id, listOf(secondItem))),
            ),
            usageByArticleId = mapOf(
                first.id to ExtractionUsage(1_000, 500, BigDecimal("0.001")),
                second.id to ExtractionUsage(2_000, 1_000, BigDecimal("0.002")),
            ),
            claimSupportJudge = FixedClaimSupportJudge(
                ClaimSupport.SUPPORTED,
                ExtractionUsage(100, 50, BigDecimal("0.0005")),
            ),
        ).run(DayWindow(first.publishedAt))

        assertEquals(3_200.0, report.metrics.getValue("usage.inputTokens"))
        assertEquals(1_600.0, report.metrics.getValue("usage.outputTokens"))
        assertEquals(0.004, report.metrics.getValue("cost.estimatedUsd"))
        assertEquals(0.002, report.metrics.getValue("cost.estimatedUsdPerNewsItem"))
    }

    @Test
    fun `reports semantic citation support separately from quote presence`() = runBlocking {
        val article = article()
        val item = newsItem(article.id)
        val report = runEval(
            articles = listOf(article),
            itemsByArticleId = mapOf(article.id to listOf(item)),
            goldSet = GoldSet(listOf(GoldCase(article.id, listOf(item)))),
            claimSupportJudge = FixedClaimSupportJudge(ClaimSupport.UNSUPPORTED),
        ).run(DayWindow(article.publishedAt))

        assertEquals(1.0, report.metrics.getValue("citationQuotePresence"))
        assertEquals(1.0, report.metrics.getValue("unsupportedClaims"))
        assertEquals(0.0, report.metrics.getValue("supportedClaims"))
        assertTrue(report.markdown.contains("Citation review sample"))
        assertTrue(report.markdown.contains("UNSUPPORTED"))
        assertTrue(report.markdown.contains(item.source.quotes.single()))
    }

    @Test
    fun `an absent citation quote is marked unsupported without a judge call`() = runBlocking {
        val article = article()
        val invalidItem = newsItem(article.id).copy(
            source = Source(article.id, listOf("This quote does not occur in the Article.")),
        )
        val report = runEval(
            articles = listOf(article),
            itemsByArticleId = mapOf(article.id to listOf(invalidItem)),
            goldSet = GoldSet(listOf(GoldCase(article.id, listOf(newsItem(article.id))))),
            claimSupportJudge = FailingClaimSupportJudge,
        ).run(DayWindow(article.publishedAt))

        assertEquals(0.0, report.metrics.getValue("citationQuotePresence"))
        assertEquals(1.0, report.metrics.getValue("unsupportedClaims"))
    }

    @Test
    fun `fails when the NewsSource returns no Gold set Articles`() = runBlocking {
        val article = article()
        val expectedItem = newsItem(article.id)
        val runner = runEval(
            articles = emptyList(),
            itemsByArticleId = emptyMap(),
            goldSet = GoldSet(listOf(GoldCase(article.id, listOf(expectedItem)))),
        )

        val failure = assertFailsWith<IllegalStateException> { runner.run(DayWindow(article.publishedAt)) }

        assertEquals("No Gold set Articles matched the NewsSource result", failure.message)
    }

    @Test
    fun `fails when the NewsSource returns only part of the Gold set`() = runBlocking {
        val presentArticle = article("gold-a")
        val missingArticle = article("gold-b")
        val presentItem = newsItem(presentArticle.id)
        val runner = runEval(
            articles = listOf(presentArticle),
            itemsByArticleId = mapOf(presentArticle.id to listOf(presentItem)),
            goldSet = GoldSet(
                listOf(
                    GoldCase(presentArticle.id, listOf(presentItem)),
                    GoldCase(missingArticle.id, listOf(newsItem(missingArticle.id))),
                ),
            ),
        )

        val failure = assertFailsWith<IllegalStateException> { runner.run(DayWindow(presentArticle.publishedAt)) }

        assertEquals("NewsSource is missing Gold set Articles: gold-b", failure.message)
    }

    private fun runEval(
        articles: List<Article>,
        itemsByArticleId: Map<String, List<NewsItem>>,
        goldSet: GoldSet,
        usageByArticleId: Map<String, ExtractionUsage> = emptyMap(),
        claimSupportJudge: ClaimSupportJudge = FixedClaimSupportJudge(ClaimSupport.SUPPORTED),
    ) = RunEval(
        newsSource = StaticNewsSource(articles),
        extractor = StaticExtractor(itemsByArticleId, usageByArticleId),
        evalStore = InMemoryEvalStore(),
        goldSet = goldSet,
        claimSupportJudge = claimSupportJudge,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private fun article(id: String = "gold-a") = Article(
        id = id,
        headline = "Markets move after the central bank decision",
        body = "The index fell 1.2% after the central bank decision.",
        outlet = "Example News",
        url = "https://example.com/news/$id",
        publishedAt = LocalDate.of(2026, 11, 3),
    )

    private fun newsItem(articleId: String) = NewsItem(
        type = NewsItemType.MARKET_MOVE,
        region = Region.US,
        subjects = listOf(Subject("S&P 500")),
        assertion = "The S&P 500 fell 1.2%.",
        direction = Direction.DOWN,
        cause = "central bank decision",
        metrics = listOf(Metric("1.2", "%")),
        source = Source(articleId, listOf("The index fell 1.2% after the central bank decision.")),
    )

    private class StaticNewsSource(private val articles: List<Article>) : NewsSource {
        override fun fetch(window: DayWindow): List<Article> = articles
    }

    private class StaticExtractor(
        private val itemsByArticleId: Map<String, List<NewsItem>>,
        private val usageByArticleId: Map<String, ExtractionUsage> = emptyMap(),
    ) : NewsItemExtractor {
        override suspend fun extract(article: Article): NewsItemExtraction = NewsItemExtraction(
            itemsByArticleId[article.id].orEmpty(),
            usageByArticleId[article.id],
        )
    }

    private class FixedClaimSupportJudge(
        private val support: ClaimSupport,
        private val usage: ExtractionUsage? = null,
    ) : ClaimSupportJudge {
        override suspend fun judge(item: NewsItem): ClaimSupportJudgment = ClaimSupportJudgment(support, usage)
    }

    private object FailingClaimSupportJudge : ClaimSupportJudge {
        override suspend fun judge(item: NewsItem): ClaimSupportJudgment =
            error("The judge must not receive an item with an absent source quote")
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-04T08:00:00Z")
    }
}
