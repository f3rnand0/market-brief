package com.marketbrief.adapters.report

import com.marketbrief.domain.EvalReport
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonEvalStoreTest {
    @Test
    fun `round-trips the versioned report and its Markdown through disk`() = withTempDirectory { directory ->
        val report = EvalReport(
            id = "eval-1",
            generatedAt = Instant.parse("2026-11-04T08:00:00Z"),
            metrics = mapOf("precision" to 0.75, "cost.estimatedUsd" to 0.003),
            version = 2,
            markdown = "# Eval\n\n| precision | 0.75 |",
        )
        val store = JsonEvalStore(directory)

        store.save(report)

        assertEquals(report, store.load(report.id))
        assertEquals(report.markdown, Files.readString(directory.resolve("${report.id}.md")))
    }

    @Test
    fun `load returns null when no evaluation was saved`() = withTempDirectory { directory ->
        assertNull(JsonEvalStore(directory).load("missing"))
    }

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = createTempDirectory("market-brief-evals")
        try {
            block(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
