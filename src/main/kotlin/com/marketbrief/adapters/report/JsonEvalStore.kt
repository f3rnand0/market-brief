package com.marketbrief.adapters.report

import com.marketbrief.domain.EvalReport
import com.marketbrief.domain.EvalStore
import com.marketbrief.reportformat.EvalReportDto
import com.marketbrief.reportformat.toDto
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

    private fun EvalReportDto.toDomain(markdown: String) =
        EvalReport(id, java.time.Instant.parse(generatedAt), metrics, version, markdown)
}
