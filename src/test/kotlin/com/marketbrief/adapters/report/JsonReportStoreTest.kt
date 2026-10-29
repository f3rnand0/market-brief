package com.marketbrief.adapters.report

import com.marketbrief.domain.InstrumentQuote
import com.marketbrief.domain.NewsItemType
import com.marketbrief.domain.Region
import com.marketbrief.domain.Report
import com.marketbrief.domain.ReportInstruments
import com.marketbrief.domain.Snapshot
import com.marketbrief.domain.item
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonReportStoreTest {

    private val date = LocalDate.of(2026, 10, 25)

    @Test
    fun `round-trips a report through disk`() = withTempDirectory { directory ->
        val store = JsonReportStore(directory)
        val report = report()

        store.save(report)

        assertEquals(report.normalized(), store.load(date)!!.normalized())
    }

    @Test
    fun `persists the grouped JSON representation`() = withTempDirectory { directory ->
        JsonReportStore(directory).save(report())

        val json = Files.readString(directory.resolve("$date.json"))

        assertTrue(json.contains("\"assetClass\": \"FIXED_INCOME\""))
        assertTrue(json.contains("\"display\": \"4.995 (+0.9 bp, +0.18%)\""))
        assertTrue(json.contains("\"missing\": ["))
    }

    @Test
    fun `load returns null when no report was saved`() = withTempDirectory { directory ->
        assertNull(JsonReportStore(directory).load(date))
    }

    private fun report() = Report(
        date = date,
        summary = "Summary",
        snapshot = Snapshot(
            quotes = listOf(
                InstrumentQuote(
                    ReportInstruments.us10y,
                    BigDecimal("4.995"),
                    BigDecimal("0.009"),
                    BigDecimal("0.18"),
                ),
                InstrumentQuote(
                    ReportInstruments.sp500,
                    BigDecimal("6912.50"),
                    BigDecimal("42.10"),
                    BigDecimal("0.61"),
                ),
                InstrumentQuote(
                    ReportInstruments.gold,
                    BigDecimal("4012.30"),
                    BigDecimal("-12.40"),
                    BigDecimal("-0.31"),
                ),
            ),
            missing = setOf(ReportInstruments.dax40),
        ),
        regionalNews = mapOf(Region.US to listOf(item(type = NewsItemType.MACRO, region = Region.US))),
        companySectorNews = emptyList(),
        otherNews = emptyList(),
    )

    private fun Report.normalized() = copy(
        snapshot = snapshot.copy(quotes = snapshot.quotes.sortedBy { it.instrument.name }),
    )

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = createTempDirectory("market-brief-reports")
        try {
            block(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
