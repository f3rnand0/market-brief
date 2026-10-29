package com.marketbrief.adapters.report

import com.marketbrief.domain.Report
import com.marketbrief.domain.ReportStore
import com.marketbrief.reportformat.ReportMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate

class JsonReportStore(private val directory: Path) : ReportStore {

    override fun save(report: Report) {
        Files.createDirectories(directory)
        val target = pathFor(report.date)
        val temporary = Files.createTempFile(directory, "${report.date}-", ".tmp")
        try {
            Files.writeString(temporary, ReportMapper.encode(report))
            Files.move(
                temporary,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: Exception) {
            Files.deleteIfExists(temporary)
            throw e
        }
    }

    override fun load(date: LocalDate): Report? {
        val path = pathFor(date)
        return if (Files.exists(path)) ReportMapper.decode(Files.readString(path)) else null
    }

    private fun pathFor(date: LocalDate): Path = directory.resolve("$date.json")
}
