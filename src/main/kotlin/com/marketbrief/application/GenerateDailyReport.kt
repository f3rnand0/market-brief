package com.marketbrief.application

import com.marketbrief.domain.DayWindow
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.MarketDataFetcher
import com.marketbrief.domain.NewsItemExtractor
import com.marketbrief.domain.NewsSource
import com.marketbrief.domain.Report
import com.marketbrief.domain.ReportAssembler
import com.marketbrief.domain.ReportStore
import java.time.LocalDate

class GenerateDailyReport(
    private val marketDataFetcher: MarketDataFetcher,
    private val newsSource: NewsSource,
    private val extractor: NewsItemExtractor,
    private val reportStore: ReportStore,
    private val instruments: Set<Instrument>,
) {
    suspend fun generate(date: LocalDate): Report {
        val snapshot = marketDataFetcher.snapshot(instruments)
        val articles = newsSource.fetch(DayWindow(date))
        val items = articles.flatMap(extractor::extract)
        val report = ReportAssembler.assemble(date, snapshot, items)
        reportStore.save(report)
        return report
    }
}
