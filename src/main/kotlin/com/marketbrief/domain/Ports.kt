package com.marketbrief.domain

import java.time.LocalDate

/**
 * The previous calendar day the report covers. The report is generated on the
 * following morning; the window boundary is the day, not the generation instant.
 */
data class DayWindow(val date: LocalDate) {
    companion object {
        fun forReportDate(reportDate: LocalDate): DayWindow = DayWindow(reportDate.minusDays(1))
    }
}

interface MarketDataFetcher {
    suspend fun snapshot(instruments: Set<Instrument>): Snapshot
}

interface NewsSource {
    fun fetch(window: DayWindow): List<Article>
}

interface NewsItemExtractor {
    /**
     * Returns only valid items. Content-level failures, such as empty or malformed
     * model output, contribute no items; provider and transport failures propagate
     * so a broken configuration or outage fails the run loudly.
     */
    suspend fun extract(article: Article): List<NewsItem>
}

interface ReportStore {
    fun save(report: Report)
    fun load(date: LocalDate): Report?
}

interface EvalStore {
    fun save(report: EvalReport)
    fun load(id: String): EvalReport?
}
