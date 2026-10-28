package com.marketbrief.domain

import java.time.LocalDate

object ReportAssembler {

    private val REGIONAL_SECTIONS = Region.entries.filter { it != Region.GLOBAL }
    private const val COMPANY_SECTOR_LIMIT = 10

    private val NewsItem.isCompanyOrSector: Boolean
        get() = type == NewsItemType.COMPANY || type == NewsItemType.SECTOR

    fun assemble(date: LocalDate, snapshot: Snapshot, items: List<NewsItem>): Report = Report(
        date = date,
        summary = buildSummary(snapshot, items),
        snapshot = snapshot,
        regionalNews = regionalSections(items),
        companySectorNews = balancedCompanySector(items),
        otherNews = otherNewsItems(items),
    )

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

    private fun regionalSections(items: List<NewsItem>): Map<Region, List<NewsItem>> = items
        .filter { it.region in REGIONAL_SECTIONS }
        .groupBy { it.region }

    private fun otherNewsItems(items: List<NewsItem>): List<NewsItem> = items
        .filter { it.region == Region.GLOBAL && !it.isCompanyOrSector }

    private fun balancedCompanySector(items: List<NewsItem>): List<NewsItem> {
        fun bucketOf(item: NewsItem) = when (item.region) {
            Region.US -> 0
            Region.CHINA -> 1
            else -> 2 // Global bucket: Europe, Latam, Asia, Global
        }
        val buckets = Array(3) { ArrayDeque<NewsItem>() }
        items
            .filter { it.isCompanyOrSector }
            .forEach { buckets[bucketOf(it)].add(it) }

        val result = mutableListOf<NewsItem>()
        while (result.size < COMPANY_SECTOR_LIMIT && buckets.any { it.isNotEmpty() }) {
            for (bucket in buckets) {
                if (result.size == COMPANY_SECTOR_LIMIT) break
                bucket.removeFirstOrNull()?.let(result::add)
            }
        }
        return result
    }
}
