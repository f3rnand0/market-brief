package com.marketbrief.adapters.report

import com.marketbrief.domain.EvalReport
import com.marketbrief.domain.EvalStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class JsonEvalStore(private val directory: Path) : EvalStore {
    override fun save(report: EvalReport) {
        Files.createDirectories(directory)
        Files.writeString(
            directory.resolve("${report.id}.json"),
            Json.encodeToString(EvalReportDto.serializer(), report.toDto()),
        )
        Files.writeString(directory.resolve("${report.id}.md"), report.markdown)
    }

    override fun load(id: String): EvalReport? {
        val path = directory.resolve("$id.json")
        if (!Files.exists(path)) return null
        val dto = Json.decodeFromString(EvalReportDto.serializer(), Files.readString(path))
        val markdown = Files.readString(directory.resolve("$id.md"), Charsets.UTF_8)
        return dto.toDomain(markdown)
    }

    private fun EvalReport.toDto() = EvalReportDto(id, generatedAt.toString(), metrics, version)
    private fun EvalReportDto.toDomain(markdown: String) =
        EvalReport(id, java.time.Instant.parse(generatedAt), metrics, version, markdown)
}

@Serializable
private data class EvalReportDto(
    val id: String,
    val generatedAt: String,
    val metrics: Map<String, Double>,
    val version: Int = 1,
)
