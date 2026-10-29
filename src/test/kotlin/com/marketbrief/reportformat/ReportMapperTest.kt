package com.marketbrief.reportformat

import com.marketbrief.domain.InstrumentQuote
import com.marketbrief.domain.NewsItemType
import com.marketbrief.domain.Region
import com.marketbrief.domain.Report
import com.marketbrief.domain.ReportInstruments
import com.marketbrief.domain.Snapshot
import com.marketbrief.domain.item
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportMapperTest {

    private val date = LocalDate.of(2026, 10, 25)

    @Test
    fun `quotes are grouped by asset class in enum order`() {
        val report = reportWith(
            quotes = listOf(
                quote(ReportInstruments.us10y, "4.995", "0.009", "0.18"),
                quote(ReportInstruments.sp500, "6912.50", "42.10", "0.61"),
            ),
            missing = setOf(ReportInstruments.nasdaqComposite),
        )

        val groups = ReportMapper.toDto(report).snapshot

        assertEquals(listOf("EQUITIES", "FIXED_INCOME"), groups.map { it.assetClass })
        assertEquals(listOf("S&P 500"), groups[0].quotes.map { it.instrument })
        assertEquals(listOf("Nasdaq Composite"), groups[0].missing)
        assertEquals(listOf("US 10Y"), groups[1].quotes.map { it.instrument })
    }

    @Test
    fun `fixed income renders basis points and percent`() {
        val report = reportWith(listOf(quote(ReportInstruments.us10y, "4.995", "0.009", "0.18")))

        val quote = ReportMapper.toDto(report).snapshot.single().quotes.single()

        assertEquals("4.995 (+0.9 bp, +0.18%)", quote.display)
        assertEquals("0.009", quote.change)
        assertEquals("0.18", quote.changePercent)
    }

    @Test
    fun `other asset classes render value and percent`() {
        val report = reportWith(listOf(quote(ReportInstruments.gold, "4012.30", "-12.40", "-0.31")))

        val quote = ReportMapper.toDto(report).snapshot.single().quotes.single()

        assertEquals("4012.30 (-0.31%)", quote.display)
    }

    @Test
    fun `report format round-trips back to the domain`() {
        val report = reportWith(
            quotes = listOf(
                quote(ReportInstruments.us10y, "4.995", "0.009", "0.18"),
                quote(ReportInstruments.sp500, "6912.50", "42.10", "0.61"),
            ),
            missing = setOf(ReportInstruments.dax40),
        )

        val decoded = ReportMapper.decode(ReportMapper.encode(report))

        assertEquals(report.normalized(), decoded.normalized())
    }

    @Test
    fun `encoded report exposes the grouped snapshot`() {
        val json = ReportMapper.encode(reportWith(listOf(quote(ReportInstruments.sp500, "6912.50", "42.10", "0.61"))))

        assertTrue(json.contains("\"assetClass\": \"EQUITIES\""))
        assertTrue(json.contains("\"display\": \"6912.50 (+0.61%)\""))
    }

    private fun reportWith(
        quotes: List<InstrumentQuote>,
        missing: Set<com.marketbrief.domain.Instrument> = emptySet(),
    ) = Report(
        date = date,
        summary = "Summary",
        snapshot = Snapshot(quotes, missing),
        regionalNews = mapOf(Region.US to listOf(item(type = NewsItemType.MACRO, region = Region.US))),
        companySectorNews = listOf(item(type = NewsItemType.COMPANY, region = Region.CHINA)),
        otherNews = listOf(item(type = NewsItemType.MARKET_MOVE, region = Region.GLOBAL)),
    )

    private fun Report.normalized() = copy(
        snapshot = snapshot.copy(quotes = snapshot.quotes.sortedBy { it.instrument.name }),
    )

    private fun quote(instrument: com.marketbrief.domain.Instrument, value: String, change: String, percent: String) =
        InstrumentQuote(
            instrument = instrument,
            value = BigDecimal(value),
            change = BigDecimal(change),
            changePercent = BigDecimal(percent),
        )
}
