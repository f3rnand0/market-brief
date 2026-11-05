package com.marketbrief.http

import com.marketbrief.application.GenerateDailyReport
import com.marketbrief.application.RunEval
import com.marketbrief.domain.ClaimSupport
import com.marketbrief.domain.ClaimSupportJudge
import com.marketbrief.domain.ClaimSupportJudgment
import com.marketbrief.domain.DayWindow
import com.marketbrief.domain.Direction
import com.marketbrief.domain.EvalStore
import com.marketbrief.domain.GoldCase
import com.marketbrief.domain.GoldSet
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote
import com.marketbrief.domain.MarketDataFetcher
import com.marketbrief.domain.Metric
import com.marketbrief.domain.NewsItem
import com.marketbrief.domain.NewsItemType
import com.marketbrief.domain.Region
import com.marketbrief.domain.ReportInstruments
import com.marketbrief.domain.Snapshot
import com.marketbrief.domain.Source
import com.marketbrief.domain.Subject
import com.marketbrief.fakes.DeterministicNewsItemExtractor
import com.marketbrief.fakes.InMemoryEvalStore
import com.marketbrief.fakes.InMemoryReportStore
import com.marketbrief.fakes.StaticCorpusNewsSource
import com.marketbrief.module
import com.marketbrief.reportformat.EvalReportDto
import com.marketbrief.reportformat.ReportMapper
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EvalRoutesTest {
    private val article = ArticleFixture.article
    private val item = ArticleFixture.item
    private val goldSet = GoldSet(listOf(GoldCase(article.id, listOf(item))))
    private val snapshot = Snapshot(
        quotes = listOf(InstrumentQuote(ReportInstruments.sp500, BigDecimal("100"), BigDecimal("1"), BigDecimal("1"))),
    )

    @Test
    fun `POST run saves and returns the eval report and GET loads it`() = testApplication {
        val evalStore = InMemoryEvalStore()
        application { module(dependencies(evalStore)) }

        val response = client.post("/eval/run")

        assertEquals(HttpStatusCode.OK, response.status)
        val dto = ReportMapper.format.decodeFromString<EvalReportDto>(response.bodyAsText())
        assertEquals(1.0, dto.metrics.getValue("precision"))
        assertNotNull(evalStore.load(dto.id))

        val loaded = client.get("/eval/${dto.id}")

        assertEquals(HttpStatusCode.OK, loaded.status)
        assertEquals(dto, ReportMapper.format.decodeFromString<EvalReportDto>(loaded.bodyAsText()))
    }

    @Test
    fun `GET missing eval report returns not found`() = testApplication {
        application { module(dependencies(InMemoryEvalStore())) }

        assertEquals(HttpStatusCode.NotFound, client.get("/eval/missing").status)
    }

    @Test
    fun `POST run returns not implemented when eval is not configured`() = testApplication {
        application {
            module(
                ReportDependencies(
                    generateDailyReport = generateDailyReport(),
                    reportStore = InMemoryReportStore(),
                ),
            )
        }

        assertEquals(HttpStatusCode.NotImplemented, client.post("/eval/run").status)
    }

    private fun dependencies(evalStore: EvalStore) = ReportDependencies(
        generateDailyReport = generateDailyReport(),
        reportStore = InMemoryReportStore(),
        runEval = RunEval(
            newsSource = StaticCorpusNewsSource(listOf(article)),
            extractor = DeterministicNewsItemExtractor(mapOf(article.id to listOf(item))),
            evalStore = evalStore,
            goldSet = goldSet,
            claimSupportJudge = FixedClaimSupportJudge(),
            clock = Clock.fixed(Instant.parse("2026-11-04T08:00:00Z"), ZoneOffset.UTC),
        ),
        evalStore = evalStore,
    )

    private fun generateDailyReport() = GenerateDailyReport(
        marketDataFetcher = FixedMarketDataFetcher(snapshot),
        newsSource = StaticCorpusNewsSource(listOf(article)),
        extractor = DeterministicNewsItemExtractor(mapOf(article.id to listOf(item))),
        reportStore = InMemoryReportStore(),
        instruments = setOf(ReportInstruments.sp500),
        clock = Clock.fixed(Instant.parse("2026-11-04T08:00:00Z"), ZoneOffset.UTC),
    )

    private class FixedMarketDataFetcher(private val snapshot: Snapshot) : MarketDataFetcher {
        override suspend fun snapshot(instruments: Set<Instrument>): Snapshot = snapshot
    }

    private class FixedClaimSupportJudge : ClaimSupportJudge {
        override suspend fun judge(item: NewsItem) = ClaimSupportJudgment(ClaimSupport.SUPPORTED)
    }

    private object ArticleFixture {
        val article = com.marketbrief.domain.Article(
            id = "eval-article",
            headline = "Central bank decision moves the S&P 500",
            body = "The S&P 500 fell 1% after the central bank decision.",
            outlet = "Example News",
            url = "https://example.com/eval-article",
            publishedAt = LocalDate.of(2026, 11, 3),
        )
        val item = NewsItem(
            type = NewsItemType.MARKET_MOVE,
            region = Region.US,
            subjects = listOf(Subject("S&P 500")),
            assertion = "The S&P 500 fell 1%.",
            direction = Direction.DOWN,
            cause = "central bank decision",
            metrics = listOf(Metric("1", "%")),
            source = Source(article.id, listOf("The S&P 500 fell 1% after the central bank decision.")),
        )
    }
}
