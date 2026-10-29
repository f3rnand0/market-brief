package com.marketbrief.application

import com.marketbrief.domain.Article
import com.marketbrief.domain.AssetClass
import com.marketbrief.domain.DayWindow
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.NewsItem
import com.marketbrief.domain.NewsItemExtractor
import com.marketbrief.domain.NewsItemType
import com.marketbrief.domain.NewsSource
import com.marketbrief.domain.Region
import com.marketbrief.domain.article
import com.marketbrief.domain.item
import com.marketbrief.fakes.DeterministicNewsItemExtractor
import com.marketbrief.fakes.FakeMarketDataFetcher
import com.marketbrief.fakes.InMemoryReportStore
import com.marketbrief.fakes.StaticCorpusNewsSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

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
        val bothStarted = CompletableDeferred<Unit>()
        val started = AtomicInteger()
        val extractor = object : NewsItemExtractor {
            override suspend fun extract(article: Article): List<NewsItem> {
                // Both articles must be in flight at once; a serial implementation times out here.
                if (started.incrementAndGet() == 2) bothStarted.complete(Unit)
                withTimeout(5.seconds) { bothStarted.await() }
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

    @Test
    fun `systemic extraction failure aborts without saving a report`() = runBlocking {
        val store = InMemoryReportStore()
        val extractor = object : NewsItemExtractor {
            override suspend fun extract(article: Article): List<NewsItem> =
                throw IllegalStateException("provider down")
        }

        val error = assertFailsWith<IllegalStateException> {
            GenerateDailyReport(
                marketDataFetcher = FakeMarketDataFetcher(),
                newsSource = StaticCorpusNewsSource(listOf(article("a1"))),
                extractor = extractor,
                reportStore = store,
                instruments = instruments,
            ).generate(date)
        }

        assertEquals("provider down", error.message)
        assertNull(store.load(date), "a failed run must not persist a partial report")
    }

    @Test
    fun `news window covers the day before the report date`() = runBlocking {
        val captured = CompletableDeferred<DayWindow>()
        val newsSource = object : NewsSource {
            override fun fetch(window: DayWindow): List<Article> {
                captured.complete(window)
                return emptyList()
            }
        }

        GenerateDailyReport(
            marketDataFetcher = FakeMarketDataFetcher(),
            newsSource = newsSource,
            extractor = DeterministicNewsItemExtractor(emptyMap()),
            reportStore = InMemoryReportStore(),
            instruments = instruments,
        ).generate(date)

        assertEquals(date.minusDays(1), captured.await().date)
    }

    @Test
    fun `extractionParallelism of one serializes extraction calls`() = runBlocking {
        val corpus = listOf(article("a1"), article("a2"))
        val firstEntered = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val calls = AtomicInteger()

        val extractor = object : NewsItemExtractor {
            override suspend fun extract(article: Article): List<NewsItem> {
                if (calls.incrementAndGet() == 1) {
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                } else {
                    secondEntered.complete(Unit)
                }
                return listOf(item(type = NewsItemType.COMPANY, subject = article.id))
            }
        }

        val job = launch {
            GenerateDailyReport(
                marketDataFetcher = FakeMarketDataFetcher(),
                newsSource = StaticCorpusNewsSource(corpus),
                extractor = extractor,
                reportStore = InMemoryReportStore(),
                instruments = instruments,
                extractionParallelism = 1,
            ).generate(date)
        }

        firstEntered.await()
        assertNull(
            withTimeoutOrNull(300) { secondEntered.await() },
            "a second extraction must wait for the only permit",
        )
        releaseFirst.complete(Unit)
        job.join()
    }
}
