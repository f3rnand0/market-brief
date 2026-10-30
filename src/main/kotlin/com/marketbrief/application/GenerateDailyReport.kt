package com.marketbrief.application

import com.marketbrief.domain.DayWindow
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.MarketDataFetcher
import com.marketbrief.domain.NewsItemExtractor
import com.marketbrief.domain.NewsSource
import com.marketbrief.domain.Report
import com.marketbrief.domain.ReportAssembler
import com.marketbrief.domain.ReportStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

class GenerateDailyReport(
    private val marketDataFetcher: MarketDataFetcher,
    private val newsSource: NewsSource,
    private val extractor: NewsItemExtractor,
    private val reportStore: ReportStore,
    private val instruments: Set<Instrument>,
    private val extractionParallelism: Int = DEFAULT_EXTRACTION_PARALLELISM,
    private val clock: Clock = Clock.system(REPORT_ZONE),
) {
    // Detached so one client disconnect cannot cancel a run shared by other callers.
    private val generationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inFlight = ConcurrentHashMap<LocalDate, Deferred<Report>>()

    init {
        require(extractionParallelism > 0) { "extractionParallelism must be positive" }
    }

    suspend fun generate(): Report {
        val date = LocalDate.now(clock)
        val generation = inFlight.computeIfAbsent(date) {
            generationScope.async(start = CoroutineStart.LAZY) { generateReport(date) }
        }
        generation.invokeOnCompletion { inFlight.remove(date, generation) }
        generation.start()
        return generation.await()
    }

    private suspend fun generateReport(date: LocalDate): Report = coroutineScope {
        val snapshot = marketDataFetcher.snapshot(instruments)
        val articles = newsSource.fetch(DayWindow.forReportDate(date))
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
