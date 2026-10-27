package com.marketbrief.application

import com.marketbrief.domain.AssetClass
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.NewsItemType
import com.marketbrief.domain.Region
import com.marketbrief.domain.article
import com.marketbrief.domain.item
import com.marketbrief.fakes.DeterministicNewsItemExtractor
import com.marketbrief.fakes.FakeMarketDataFetcher
import com.marketbrief.fakes.InMemoryReportStore
import com.marketbrief.fakes.StaticCorpusNewsSource
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GenerateDailyReportTest {

    private val date = LocalDate.of(2026, 10, 25)
    private val instruments = setOf(Instrument("S&P 500", AssetClass.EQUITIES))

    @Test
    fun `pipeline assembles snapshot plus extracted items and saves the report`() = runBlocking {
        val corpus = listOf(article("a1"), article("a2"))
        val extracted = mapOf(
            "a1" to listOf(item(type = NewsItemType.COMPANY, region = Region.US, subject = "Apple")),
            "a2" to listOf(item(type = NewsItemType.MACRO, region = Region.CHINA, subject = "PBOC")),
        )
        val store = InMemoryReportStore()

        val useCase = GenerateDailyReport(
            marketDataFetcher = FakeMarketDataFetcher(),
            newsSource = StaticCorpusNewsSource(corpus),
            extractor = DeterministicNewsItemExtractor(extracted),
            reportStore = store,
            instruments = instruments,
        )

        val report = useCase.generate(date)

        assertEquals(date, report.date)
        assertEquals(instruments, report.snapshot.quotes.map { it.instrument }.toSet())
        assertEquals(listOf("PBOC"), report.regionalNews[Region.CHINA]!!.flatMap { it.subjects }.map { it.name })
        assertEquals(listOf("Apple"), report.companySectorNews.flatMap { it.subjects }.map { it.name })
        assertSame(report, store.load(date), "generated report must be persisted")
    }
}
