package com.marketbrief.fakes

import com.marketbrief.domain.Article
import com.marketbrief.domain.DayWindow
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote
import com.marketbrief.domain.MarketDataFetcher
import com.marketbrief.domain.NewsItem
import com.marketbrief.domain.NewsItemExtractor
import com.marketbrief.domain.NewsSource
import com.marketbrief.domain.Report
import com.marketbrief.domain.ReportStore
import com.marketbrief.domain.Snapshot
import java.math.BigDecimal
import java.time.LocalDate

class FakeMarketDataFetcher : MarketDataFetcher {
    override fun snapshot(instruments: Set<Instrument>): Snapshot =
        Snapshot(instruments.map { InstrumentQuote(it, BigDecimal("100"), BigDecimal("1.0"), BigDecimal("1.0")) })
}

class StaticCorpusNewsSource(private val articles: List<Article>) : NewsSource {
    override fun fetch(window: DayWindow): List<Article> = articles
}

class DeterministicNewsItemExtractor(private val itemsByArticleId: Map<String, List<NewsItem>>) :
    NewsItemExtractor {
    override fun extract(article: Article): List<NewsItem> =
        itemsByArticleId[article.id].orEmpty()
}

class InMemoryReportStore : ReportStore {
    private val reports = mutableMapOf<LocalDate, Report>()

    override fun save(report: Report) {
        reports[report.date] = report
    }

    override fun load(date: LocalDate): Report? = reports[date]
}
