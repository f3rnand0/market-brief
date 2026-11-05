package com.marketbrief.adapters.news

import com.marketbrief.domain.Direction
import com.marketbrief.domain.GoldCase
import com.marketbrief.domain.GoldSet
import com.marketbrief.domain.Metric
import com.marketbrief.domain.NewsItem
import com.marketbrief.domain.NewsItemType
import com.marketbrief.domain.Region
import com.marketbrief.domain.Source
import com.marketbrief.domain.Subject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object JsonGoldSet {
    fun fromResource(resource: String = "gold/index.json"): GoldSet {
        val loader = Thread.currentThread().contextClassLoader
        val ids =
            loader.getResourceAsStream(resource)?.bufferedReader()?.use {
                Json.decodeFromString<List<String>>(it.readText())
            }
                ?: error("Gold set index not found: $resource")
        val cases = ids.map { id ->
            val file = "gold/$id.json"
            val entry = loader.getResourceAsStream(file)?.bufferedReader()?.use {
                Json.decodeFromString<GoldFile>(it.readText())
            } ?: error("Gold set case not found: $file")
            GoldCase(id, entry.items.map { it.toDomain(id) })
        }
        return GoldSet(cases)
    }
}

@Serializable
private data class GoldFile(val items: List<GoldItem> = emptyList())

@Serializable
private data class GoldItem(
    val type: String,
    val region: String,
    val subjects: List<String>,
    val assertion: String,
    val direction: String? = null,
    val cause: String? = null,
    val metrics: List<GoldMetric> = emptyList(),
    val quotes: List<String>,
) {
    fun toDomain(articleId: String) = NewsItem(
        type = NewsItemType.valueOf(type),
        region = Region.valueOf(region),
        subjects = subjects.map(::Subject),
        assertion = assertion,
        direction = direction?.let(Direction::valueOf),
        cause = cause,
        metrics = metrics.map { Metric(it.value, it.unit) },
        source = Source(articleId, quotes),
    )
}

@Serializable
private data class GoldMetric(val value: String, val unit: String)
