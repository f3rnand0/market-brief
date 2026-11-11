package com.marketbrief.domain

import java.time.LocalDate
import java.util.Locale

object ReportAssembler {

    private val REGIONAL_SECTIONS = Region.entries.filter { it != Region.GLOBAL }
    private const val NEWS_ITEMS_PER_GROUP_LIMIT = 4
    private const val REGIONAL_OUTLET_LIMIT = 2

    private val NewsItem.isCompanyOrSector: Boolean
        get() = type == NewsItemType.COMPANY || type == NewsItemType.SECTOR

    fun assemble(date: LocalDate, snapshot: Snapshot, articles: List<Article>, items: List<NewsItem>): Report {
        val deduplicatedItems = deduplicateItems(articles, items)
        return Report(
            date = date,
            summary = buildSummary(snapshot, items),
            snapshot = snapshot,
            regionalNews = regionalSections(articles, deduplicatedItems),
            companySectorNews = balancedCompanySector(deduplicatedItems),
            otherNews = otherNewsItems(deduplicatedItems),
        )
    }

    private fun deduplicateItems(articles: List<Article>, items: List<NewsItem>): List<NewsItem> {
        val articlesById = articles.associateBy(Article::id)
        val seenEvents = mutableSetOf<String>()
        return items
            .sortedWith(
                compareByDescending<NewsItem> { articlesById[it.source.articleId]?.publishedAt }
                    .thenBy { articlesById[it.source.articleId]?.outlet.orEmpty() }
                    .thenBy { it.source.articleId }
                    .thenBy { it.assertion },
            )
            .filter { seenEvents.add(it.eventKey()) }
    }

    private fun buildSummary(snapshot: Snapshot, items: List<NewsItem>): String {
        val quotes = snapshot.quotes.associateBy { it.instrument }
        val causes = causesBySummaryInstrument(items)
        val sentences = ReportInstruments.summaryIndices.mapNotNull { instrument ->
            val quote = quotes[instrument]
            when {
                quote != null -> buildIndexSentence(instrument, quote, causes[instrument])
                instrument in snapshot.missing -> "${instrument.name}: data unavailable."
                else -> null
            }
        }
        return sentences.ifEmpty { listOf("No major index moves.") }.joinToString(" ")
    }

    private fun causesBySummaryInstrument(items: List<NewsItem>): Map<Instrument, Set<String>> {
        val causes = linkedMapOf<Instrument, LinkedHashSet<String>>()
        for (item in items) {
            if (item.type != NewsItemType.MARKET_MOVE) continue
            val cause = item.cause?.trimEnd('.') ?: continue
            val matched = item.subjects
                .mapNotNull { ReportInstruments.summaryInstrumentFor(it.name) }
                .distinct()
            for (instrument in matched) {
                causes.getOrPut(instrument) { linkedSetOf() }.add(cause)
            }
        }
        return causes
    }

    private fun buildIndexSentence(instrument: Instrument, quote: InstrumentQuote, causes: Set<String>?): String {
        val move = "${instrument.name}: ${quote.value.toPlainString()} (${quote.changePercent.toPlainString()}%)."
        return if (causes.isNullOrEmpty()) {
            move
        } else {
            "$move Caused by: ${causes.joinToString("; ")}."
        }
    }

    private fun regionalSections(articles: List<Article>, items: List<NewsItem>): Map<Region, List<NewsItem>> {
        val articlesById = articles.associateBy(Article::id)
        return items
            .filter { it.region in REGIONAL_SECTIONS && !it.isCompanyOrSector }
            .groupBy { it.region }
            .mapValues { (_, regionalItems) ->
                val outlets = mutableMapOf<String, Int>()
                regionalItems
                    .sortedWith(
                        compareByDescending<NewsItem> { articlesById[it.source.articleId]?.publishedAt }
                            .thenBy { articlesById[it.source.articleId]?.outlet.orEmpty() }
                            .thenBy { it.source.articleId },
                    )
                    .filter { item ->
                        val article = articlesById[item.source.articleId]
                        val outlet = article?.outlet ?: item.source.articleId
                        if ((outlets[outlet] ?: 0) >= REGIONAL_OUTLET_LIMIT) {
                            false
                        } else {
                            outlets[outlet] = (outlets[outlet] ?: 0) + 1
                            true
                        }
                    }
                    .take(NEWS_ITEMS_PER_GROUP_LIMIT)
            }
            .filterValues(List<NewsItem>::isNotEmpty)
    }

    private fun NewsItem.eventKey(): String = listOf(
        type.name,
        canonicalSubjects().joinToString("|"),
        assertion.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " "),
    ).joinToString(":")

    private fun NewsItem.canonicalSubjects(): List<String> = subjects
        .map { it.name.canonicalSubject() }
        .distinct()
        .sorted()

    private fun String.canonicalSubject(): String = trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
    private fun otherNewsItems(items: List<NewsItem>): List<NewsItem> = items
        .filter { it.region == Region.GLOBAL && !it.isCompanyOrSector }
        .take(NEWS_ITEMS_PER_GROUP_LIMIT)

    private fun balancedCompanySector(items: List<NewsItem>): List<NewsItem> {
        fun bucketOf(item: NewsItem) = when (item.region) {
            Region.US -> 0
            Region.CHINA -> 1
            else -> 2 // Global bucket: Europe, Latam, Asia, Global
        }
        val buckets = Array(3) { ArrayDeque<NewsItem>() }
        val companies = mutableSetOf<String>()
        items
            .filter { it.isCompanyOrSector }
            .forEach { item ->
                val primaryCompany = if (item.type == NewsItemType.COMPANY) {
                    item.subjects.firstOrNull()?.name?.canonicalSubject()
                } else {
                    null
                }
                if (primaryCompany == null || companies.add(primaryCompany)) {
                    buckets[bucketOf(item)].add(item)
                }
            }

        val result = mutableListOf<NewsItem>()
        while (result.size < NEWS_ITEMS_PER_GROUP_LIMIT && buckets.any { it.isNotEmpty() }) {
            for (bucket in buckets) {
                if (result.size == NEWS_ITEMS_PER_GROUP_LIMIT) break
                bucket.removeFirstOrNull()?.let(result::add)
            }
        }
        return result
    }
}
