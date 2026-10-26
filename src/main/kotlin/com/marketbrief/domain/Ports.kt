package com.marketbrief.domain

import java.time.LocalDate

/**
 * Every port has a production adapter plus a fallback/fake.
 */

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
