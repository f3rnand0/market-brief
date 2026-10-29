package com.marketbrief.http

import com.marketbrief.application.GenerateDailyReport
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote
import com.marketbrief.domain.MarketDataFetcher
import com.marketbrief.domain.NewsItemType
import com.marketbrief.domain.Region
import com.marketbrief.domain.Report
import com.marketbrief.domain.ReportAssembler
import com.marketbrief.domain.ReportInstruments
import com.marketbrief.domain.Snapshot
import com.marketbrief.domain.article
import com.marketbrief.domain.item
import com.marketbrief.fakes.DeterministicNewsItemExtractor
import com.marketbrief.fakes.InMemoryReportStore
import com.marketbrief.fakes.StaticCorpusNewsSource
import com.marketbrief.module
import com.marketbrief.wire.ReportDto
import com.marketbrief.wire.ReportMapper
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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

class ReportRoutesTest {

    private val date = LocalDate.of(2026, 10, 25)
    private val clock = Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZoneOffset.UTC)
    private val snapshot = Snapshot(
        quotes = listOf(
            quote(ReportInstruments.sp500, "6912.50", "42.10", "0.61"),
            quote(ReportInstruments.us10y, "4.995", "0.009", "0.18"),
            quote(ReportInstruments.gold, "4012.30", "-12.40", "-0.31"),
        ),
        missing = setOf(ReportInstruments.nasdaqComposite, ReportInstruments.dax40),
    )
    private val items = listOf(
        item(type = NewsItemType.MACRO, region = Region.US, subject = "Federal Reserve"),
        item(type = NewsItemType.COMPANY, region = Region.CHINA, subject = "Tencent"),
        item(type = NewsItemType.MARKET_MOVE, region = Region.GLOBAL, subject = "Oil"),
    )

    @Test
    fun `generate persists the report and returns its JSON`() = testApplication {
        val store = InMemoryReportStore()
        application { module(dependencies(store)) }

        val response = client.post("/report/generate?date=$date")

        assertEquals(HttpStatusCode.OK, response.status)
        val dto = ReportMapper.format.decodeFromString<ReportDto>(response.bodyAsText())
        assertEquals(date.toString(), dto.date)
        assertEquals("4.995 (+0.9 bp, +0.18%)", fixedIncomeQuote(dto).display)
        assertNotNull(store.load(date))
    }

    @Test
    fun `generate without a date uses the current report date`() = testApplication {
        val store = InMemoryReportStore()
        application { module(dependencies(store)) }

        val response = client.post("/report/generate")

        assertEquals(HttpStatusCode.OK, response.status)
        val dto = ReportMapper.format.decodeFromString<ReportDto>(response.bodyAsText())
        assertEquals("2026-10-26", dto.date)
    }

    @Test
    fun `GET report returns the stored JSON with grouped snapshot and news`() = testApplication {
        val store = InMemoryReportStore().apply { save(report()) }
        application { module(dependencies(store)) }

        val response = client.get("/report/$date")

        assertEquals(HttpStatusCode.OK, response.status)
        val dto = ReportMapper.format.decodeFromString<ReportDto>(response.bodyAsText())
        assertEquals(date.toString(), dto.date)
        assertEquals("S&P 500: 6912.50 (0.61%). Nasdaq Composite: data unavailable.", dto.summary)
        assertEquals(listOf("EQUITIES", "FIXED_INCOME", "COMMODITIES"), dto.snapshot.map { it.assetClass })
        assertEquals(listOf("Nasdaq Composite", "DAX 40"), dto.snapshot.first().missing)
        assertTrue(dto.regionalNews.containsKey("US"))
        assertTrue(dto.companySectorNews.isNotEmpty())
        assertTrue(dto.otherNews.isNotEmpty())
    }

    @Test
    fun `GET html renders snapshot values, gaps, and news`() = testApplication {
        val store = InMemoryReportStore().apply { save(report()) }
        application { module(dependencies(store)) }

        val response = client.get("/report/$date.html")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()?.match(ContentType.Text.Html) == true)
        val html = response.bodyAsText()
        assertTrue(html.contains("<html"))
        assertTrue(html.contains("Market brief — $date"))
        assertTrue(html.contains("S&amp;P 500"))
        assertTrue(html.contains("4.995 (+0.9 bp, +0.18%)"))
        assertTrue(html.contains("data unavailable"))
        assertTrue(html.contains("Federal Reserve"))
    }

    @Test
    fun `missing report returns not found`() = testApplication {
        application { module(dependencies(InMemoryReportStore())) }

        assertEquals(HttpStatusCode.NotFound, client.get("/report/$date").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/report/$date.html").status)
    }

    @Test
    fun `invalid date is rejected`() = testApplication {
        application { module(dependencies(InMemoryReportStore())) }

        assertEquals(HttpStatusCode.BadRequest, client.get("/report/not-a-date").status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/report/generate?date=tomorrow").status)
    }

    private fun dependencies(store: InMemoryReportStore) = ReportDependencies(
        generateDailyReport = GenerateDailyReport(
            marketDataFetcher = FixedMarketDataFetcher(snapshot),
            newsSource = StaticCorpusNewsSource(listOf(article("a1"))),
            extractor = DeterministicNewsItemExtractor(mapOf("a1" to items)),
            reportStore = store,
            instruments = ReportInstruments.all,
        ),
        reportStore = store,
        clock = clock,
    )

    private fun report(): Report = ReportAssembler.assemble(date, snapshot, items)

    private fun fixedIncomeQuote(dto: ReportDto) = dto.snapshot
        .first { it.assetClass == "FIXED_INCOME" }
        .quotes
        .single()

    private fun quote(instrument: Instrument, value: String, change: String, percent: String) =
        InstrumentQuote(instrument, BigDecimal(value), BigDecimal(change), BigDecimal(percent))

    private class FixedMarketDataFetcher(private val snapshot: Snapshot) : MarketDataFetcher {
        override suspend fun snapshot(instruments: Set<Instrument>): Snapshot = snapshot
    }
}
