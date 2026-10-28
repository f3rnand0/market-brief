package com.marketbrief.domain

import java.time.LocalDate

data class Report(
    val date: LocalDate,
    val summary: String,
    val snapshot: Snapshot,
    val regionalNews: Map<Region, List<NewsItem>>,
    val companySectorNews: List<NewsItem>,
    val otherNews: List<NewsItem>,
)
