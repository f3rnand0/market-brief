package com.marketbrief.domain

import java.time.LocalDate

object ReportAssembler {

    private val REGIONAL_SECTIONS = listOf(Region.US, Region.CHINA, Region.EUROPE, Region.LATAM, Region.ASIA)
    private val SUMMARY_INDICES = listOf("S&P 500", "Nasdaq Composite")
    private const val COMPANY_SECTOR_LIMIT = 10

    fun assemble(date: LocalDate, snapshot: Snapshot, items: List<NewsItem>): Report =
        Report(
            date = date,
            summary = buildSummary(items),
            snapshot = snapshot,
            regionalNews = regionalSections(items),
            companySectorNews = balancedCompanySector(items),
        )

    private fun buildSummary(items: List<NewsItem>): String {
        val moves = items.filter { it.type == NewsItemType.MARKET_MOVE }
        val (indexMoves, _) = moves.partition { item -> item.subjects.any { it.name in SUMMARY_INDICES } }
        if (indexMoves.isEmpty()) return "No major index moves."
        return indexMoves.joinToString(" ") { item ->
            val cause = item.cause?.let { " Caused by: $it." } ?: ""
            item.assertion.trimEnd('.') + "." + cause
        }
    }

    private fun regionalSections(items: List<NewsItem>): Map<Region, List<NewsItem>> =
        items
            .filter { it.region in REGIONAL_SECTIONS }
            .groupBy { it.region }

    private fun balancedCompanySector(items: List<NewsItem>): List<NewsItem> {
        fun bucketOf(item: NewsItem) = when (item.region) {
            Region.US -> 0
            Region.CHINA -> 1
            else -> 2 // Global bucket: Europe, Latam, Asia, Global
        }
        val buckets = Array(3) { ArrayDeque<NewsItem>() }
        items
            .filter { it.type == NewsItemType.COMPANY || it.type == NewsItemType.SECTOR }
            .forEach { buckets[bucketOf(it)].add(it) }

        val result = mutableListOf<NewsItem>()
        while (result.size < COMPANY_SECTOR_LIMIT) {
            val before = result.size
            for (bucket in buckets) bucket.removeFirstOrNull()?.let(result::add)
            if (result.size == before) break // all buckets empty
        }
        return result
    }
}
