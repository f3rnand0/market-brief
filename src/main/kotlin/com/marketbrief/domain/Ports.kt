package com.marketbrief.domain

import java.time.LocalDate

data class DayWindow(val date: LocalDate)

interface MarketData {
    fun snapshot(instruments: Set<Instrument>): Snapshot
}

interface NewsSource {
    fun fetch(window: DayWindow): List<Article>
}

interface NewsItemExtractor {
    fun extract(article: Article): List<NewsItem>
}

interface ReportStore {
    fun save(report: Report)
    fun load(date: LocalDate): Report?
}

interface EvalStore {
    fun save(report: EvalReport)
    fun load(id: String): EvalReport?
}
