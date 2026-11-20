package com.marketbrief.adapters.report

import com.marketbrief.domain.Direction
import com.marketbrief.domain.EvalCheckpoint
import com.marketbrief.domain.EvalCheckpointStore
import com.marketbrief.domain.ExtractionUsage
import com.marketbrief.domain.Metric
import com.marketbrief.domain.NewsItem
import com.marketbrief.domain.NewsItemExtraction
import com.marketbrief.domain.NewsItemType
import com.marketbrief.domain.Region
import com.marketbrief.domain.Source
import com.marketbrief.domain.Subject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

class JsonEvalCheckpointStore(private val directory: Path) : EvalCheckpointStore {
    override fun load(): EvalCheckpoint? {
        val path = directory.resolve(FILE_NAME)
        if (!Files.exists(path)) return null
        return Json.decodeFromString<EvalCheckpointDto>(Files.readString(path)).toDomain()
    }

    override fun save(checkpoint: EvalCheckpoint) {
        Files.createDirectories(directory)
        val temporaryPath = Files.createTempFile(directory, FILE_PREFIX, FILE_SUFFIX)
        try {
            Files.writeString(temporaryPath, Json.encodeToString(checkpoint.toDto()))
            Files.move(temporaryPath, directory.resolve(FILE_NAME), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporaryPath)
        }
    }

    override fun clear() {
        Files.deleteIfExists(directory.resolve(FILE_NAME))
    }

    private fun EvalCheckpoint.toDto() = EvalCheckpointDto(
        goldSetVersion,
        articleIds,
        extractions.mapValues { (_, extraction) -> extraction.toDto() },
    )

    private fun EvalCheckpointDto.toDomain() = EvalCheckpoint(
        goldSetVersion,
        articleIds,
        extractions.mapValues { (_, extraction) -> extraction.toDomain() },
    )

    private fun NewsItemExtraction.toDto() = ExtractionDto(
        items = items.map { item ->
            NewsItemDto(
                item.type.name,
                item.region.name,
                item.subjects.map(Subject::name),
                item.assertion,
                item.direction?.name,
                item.cause,
                item.metrics.map { MetricDto(it.value, it.unit) },
                SourceDto(item.source.articleId, item.source.quotes),
            )
        },
        usage = usage?.let { UsageDto(it.inputTokens, it.outputTokens, it.estimatedCostUsd?.toPlainString()) },
    )

    private fun ExtractionDto.toDomain() = NewsItemExtraction(
        items.map { item ->
            NewsItem(
                NewsItemType.valueOf(item.type),
                Region.valueOf(item.region),
                item.subjects.map(::Subject),
                item.assertion,
                item.direction?.let(Direction::valueOf),
                item.cause,
                item.metrics.map { Metric(it.value, it.unit) },
                Source(item.source.articleId, item.source.quotes),
            )
        },
        usage?.let { ExtractionUsage(it.inputTokens, it.outputTokens, it.estimatedCostUsd?.let(::BigDecimal)) },
    )

    @Serializable
    private data class EvalCheckpointDto(
        val goldSetVersion: String,
        val articleIds: List<String>,
        val extractions: Map<String, ExtractionDto>,
    )

    @Serializable
    private data class ExtractionDto(val items: List<NewsItemDto>, val usage: UsageDto?)

    @Serializable
    private data class NewsItemDto(
        val type: String,
        val region: String,
        val subjects: List<String>,
        val assertion: String,
        val direction: String?,
        val cause: String?,
        val metrics: List<MetricDto>,
        val source: SourceDto,
    )

    @Serializable
    private data class MetricDto(val value: String, val unit: String)

    @Serializable
    private data class SourceDto(val articleId: String, val quotes: List<String>)

    @Serializable
    private data class UsageDto(val inputTokens: Long?, val outputTokens: Long?, val estimatedCostUsd: String?)

    private companion object {
        const val FILE_NAME = "checkpoint.json"
        const val FILE_PREFIX = "checkpoint-"
        const val FILE_SUFFIX = ".tmp"
    }
}
