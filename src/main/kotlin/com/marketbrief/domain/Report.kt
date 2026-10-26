package com.marketbrief.domain

import java.time.LocalDate

/**
 * The assembled output document: the four sections built from a
 * Snapshot plus extracted NewsItems.
 */
data class Report(
    val date: LocalDate,
    val summary: String,
    val snapshot: Snapshot,
    val regionalNews: Map<Region, List<NewsItem>>,
    val companySectorNews: List<NewsItem>,
)
