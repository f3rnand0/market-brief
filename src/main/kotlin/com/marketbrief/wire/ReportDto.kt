package com.marketbrief.wire

import kotlinx.serialization.Serializable

/**
 * The serializable report representation shared by the JSON store and the HTTP
 * layer. Domain objects stay free of serialization annotations; this is the
 * single wire shape both persistence and presentation map through.
 */
@Serializable
data class ReportDto(
    val date: String,
    val summary: String,
    val snapshot: List<SnapshotGroupDto>,
    val regionalNews: Map<String, List<NewsItemDto>>,
    val companySectorNews: List<NewsItemDto>,
    val otherNews: List<NewsItemDto>,
)

/** Quotes grouped by asset class at render time; the domain snapshot stays flat. */
@Serializable
data class SnapshotGroupDto(
    val assetClass: String,
    val quotes: List<QuoteDto> = emptyList(),
    val missing: List<String> = emptyList(),
)

@Serializable
data class QuoteDto(
    val instrument: String,
    val assetClass: String,
    val value: String,
    val change: String,
    val changePercent: String,
    val display: String,
)

@Serializable
data class NewsItemDto(
    val type: String,
    val region: String,
    val subjects: List<String>,
    val assertion: String,
    val direction: String? = null,
    val cause: String? = null,
    val metrics: List<MetricDto> = emptyList(),
    val source: SourceDto,
)

@Serializable
data class MetricDto(val value: String, val unit: String)

@Serializable
data class SourceDto(val articleId: String, val quotes: List<String>)

@Serializable
data class ErrorDto(val error: String)
