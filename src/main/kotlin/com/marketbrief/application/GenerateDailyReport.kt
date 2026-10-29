package com.marketbrief.application

import com.marketbrief.domain.DayWindow
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.MarketDataFetcher
import com.marketbrief.domain.NewsItemExtractor
import com.marketbrief.domain.NewsSource
import com.marketbrief.domain.Report
import com.marketbrief.domain.ReportAssembler
import com.marketbrief.domain.ReportStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class GenerateDailyReport(
    private val marketDataFetcher: MarketDataFetcher,
    private val newsSource: NewsSource,
    private val extractor: NewsItemExtractor,
    private val reportStore: ReportStore,
    private val instruments: Set<Instrument>,
    private val extractionParallelism: Int = DEFAULT_EXTRACTION_PARALLELISM,
    private val clock: Clock = Clock.system(REPORT_ZONE),
) {
    init {
        require(extractionParallelism > 0) { "extractionParallelism must be positive" }
    }

    suspend fun generate(date: LocalDate = LocalDate.now(clock)): Report = coroutineScope {
        val snapshot = marketDataFetcher.snapshot(instruments)
        val articles = newsSource.fetch(DayWindow.forReportDate(date))
        // Requests are independent and non-blocking, so fan out under a fixed permit count;
        // awaitAll preserves article order for the eval loop.
        val permits = Semaphore(extractionParallelism)
        val items = articles
            .map { article -> async { permits.withPermit { extractor.extract(article) } } }
            .awaitAll()
            .flatten()
        val report = ReportAssembler.assemble(date, snapshot, items)
        reportStore.save(report)
        report
    }

    companion object {
        const val DEFAULT_EXTRACTION_PARALLELISM = 4
        val REPORT_ZONE: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
