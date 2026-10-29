package com.marketbrief.application

import com.marketbrief.domain.Article
import com.marketbrief.domain.AssetClass
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.NewsItem
import com.marketbrief.domain.NewsItemExtractor
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
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
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

    @Test
    fun `extracts articles concurrently and preserves article order`() = runBlocking {
        val corpus = listOf(article("a1"), article("a2"))
        val barrier = CyclicBarrier(2)
        val extractor = object : NewsItemExtractor {
            override fun extract(article: Article): List<NewsItem> {
                // Both articles must be in flight at once; a serial implementation times out here.
                barrier.await(5, TimeUnit.SECONDS)
                return listOf(item(type = NewsItemType.COMPANY, subject = article.id))
            }
        }

        val report = GenerateDailyReport(
            marketDataFetcher = FakeMarketDataFetcher(),
            newsSource = StaticCorpusNewsSource(corpus),
            extractor = extractor,
            reportStore = InMemoryReportStore(),
            instruments = instruments,
        ).generate(date)

        assertEquals(
            listOf("a1", "a2"),
            report.companySectorNews.flatMap { item -> item.subjects }.map { it.name },
            "concurrent extraction must keep the article order",
        )
    }
}
