package com.marketbrief.domain

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportAssemblerTest {

    private val date = LocalDate.of(2026, 10, 25)
    private val snapshot = Snapshot(emptyList())

    @Test
    fun `regional news is grouped by region, Global items excluded`() {
        val items = listOf(
            item(type = NewsItemType.MACRO, region = Region.US, subject = "Fed"),
            item(type = NewsItemType.MACRO, region = Region.CHINA, subject = "PBOC"),
            item(type = NewsItemType.MACRO, region = Region.US, subject = "Treasuries"),
            item(type = NewsItemType.MACRO, region = Region.GLOBAL, subject = "Oil"),
        )

        val regional = ReportAssembler.assemble(date, snapshot, items).regionalNews

        assertEquals(setOf(Region.US, Region.CHINA), regional.keys)
        assertEquals(2, regional[Region.US]!!.size)
    }

    @Test
    fun `company and sector items appear only in the company-sector section`() {
        val company = item(type = NewsItemType.COMPANY, region = Region.US, subject = "Apple")
        val sector = item(type = NewsItemType.SECTOR, region = Region.GLOBAL, subject = "Semiconductors")
        val macro = item(type = NewsItemType.MACRO, region = Region.US, subject = "Fed")

        val report = ReportAssembler.assemble(date, snapshot, listOf(company, sector, macro))

        assertEquals(listOf(macro), report.regionalNews[Region.US])
        assertTrue(
            report.regionalNews.values.flatten().none {
                it.type == NewsItemType.COMPANY || it.type == NewsItemType.SECTOR
            },
            "company and sector items must not be duplicated into regional sections",
        )
        assertEquals(listOf(company, sector), report.companySectorNews)
    }

    @Test
    fun `company-sector section is balanced, never US-only when alternatives exist`() {
        val items = listOf(
            item(type = NewsItemType.COMPANY, region = Region.US, subject = "Apple"),
            item(type = NewsItemType.COMPANY, region = Region.US, subject = "Microsoft"),
            item(type = NewsItemType.COMPANY, region = Region.CHINA, subject = "Tencent"),
            item(type = NewsItemType.SECTOR, region = Region.GLOBAL, subject = "Semiconductors"),
        )

        val section = ReportAssembler.assemble(date, snapshot, items).companySectorNews

        assertEquals(4, section.size)
        assertTrue(section.any { it.region == Region.CHINA }, "must include China")
        assertTrue(section.any { it.region == Region.GLOBAL }, "must include Global")
    }

    @Test
    fun `company-sector section never exceeds its limit`() {
        val items = buildList {
            repeat(4) { add(item(type = NewsItemType.COMPANY, region = Region.US, subject = "US $it")) }
            repeat(4) { add(item(type = NewsItemType.COMPANY, region = Region.CHINA, subject = "China $it")) }
            repeat(4) { add(item(type = NewsItemType.SECTOR, region = Region.GLOBAL, subject = "Global $it")) }
        }

        val section = ReportAssembler.assemble(date, snapshot, items).companySectorNews

        assertEquals(10, section.size)
    }

    @Test
    fun `summary takes index numbers from the snapshot and causes from prose`() {
        val items = listOf(
            item(
                type = NewsItemType.MARKET_MOVE,
                subject = "S&P 500",
                assertion = "S&P 500 fell 1.2%",
                cause = "hawkish Fed comments",
            ),
            item(
                type = NewsItemType.MARKET_MOVE,
                subject = "Nasdaq Composite",
                assertion = "Nasdaq dropped 2%",
            ),
            item(type = NewsItemType.COMPANY, subject = "Apple"),
        )

        val summary = ReportAssembler.assemble(date, indexSnapshot(), items).summary

        assertTrue(summary.contains("S&P 500: 6912.50 (0.61%)."))
        assertTrue(summary.contains("Nasdaq Composite: 23950.75 (-0.70%)."))
        assertTrue(summary.contains("Caused by: hawkish Fed comments."))
        assertTrue(!summary.contains("1.2%"), "prose numbers must not reach the summary")
        assertTrue(!summary.contains("Apple"))
    }

    @Test
    fun `summary matches index aliases and emits one sentence per index`() {
        val items = listOf(
            item(type = NewsItemType.MARKET_MOVE, subject = "S&P500", cause = "hawkish Fed comments"),
            item(type = NewsItemType.MARKET_MOVE, subject = "S&P 500", cause = "hawkish Fed comments"),
            item(type = NewsItemType.MARKET_MOVE, subject = "S&P 500 Index", cause = "weak earnings"),
            item(type = NewsItemType.MARKET_MOVE, subject = "Nasdaq", cause = "tech selloff"),
        )

        val summary = ReportAssembler.assemble(date, indexSnapshot(), items).summary

        assertEquals(1, Regex("S&P 500:").findAll(summary).count())
        assertTrue(summary.contains("Caused by: hawkish Fed comments; weak earnings."))
        assertTrue(summary.contains("Nasdaq Composite: 23950.75 (-0.70%). Caused by: tech selloff."))
    }

    @Test
    fun `summary states no moves for a complete snapshot without index quotes`() {
        val items = listOf(
            item(
                type = NewsItemType.MARKET_MOVE,
                subject = "S&P 500",
                assertion = "S&P 500 fell 1.2%",
                cause = "hawkish Fed comments",
            ),
        )

        val summary = ReportAssembler.assemble(date, Snapshot(emptyList()), items).summary

        assertEquals("No major index moves.", summary)
    }

    @Test
    fun `missing index data is reported as unavailable, not as no moves`() {
        val snapshot = Snapshot(
            quotes = emptyList(),
            missing = setOf(ReportInstruments.sp500, ReportInstruments.nasdaqComposite),
        )

        val summary = ReportAssembler.assemble(date, snapshot, emptyList()).summary

        assertEquals("S&P 500: data unavailable. Nasdaq Composite: data unavailable.", summary)
    }

    @Test
    fun `partially missing index data still reports the available index`() {
        val snapshot = Snapshot(
            quotes = indexSnapshot().quotes.filter { it.instrument == ReportInstruments.sp500 },
            missing = setOf(ReportInstruments.nasdaqComposite),
        )

        val summary = ReportAssembler.assemble(date, snapshot, emptyList()).summary

        assertEquals("S&P 500: 6912.50 (0.61%). Nasdaq Composite: data unavailable.", summary)
    }

    @Test
    fun `a joint index move attaches its cause to every matched index`() {
        val jointMove = item(
            type = NewsItemType.MARKET_MOVE,
            subject = "S&P 500",
            cause = "hawkish Fed comments",
        ).copy(subjects = listOf(Subject("S&P 500"), Subject("Nasdaq Composite")))

        val summary = ReportAssembler.assemble(date, indexSnapshot(), listOf(jointMove)).summary

        assertTrue(summary.contains("S&P 500: 6912.50 (0.61%). Caused by: hawkish Fed comments."))
        assertTrue(summary.contains("Nasdaq Composite: 23950.75 (-0.70%). Caused by: hawkish Fed comments."))
    }

    @Test
    fun `global macro and market items are kept as other news`() {
        val macro = item(type = NewsItemType.MACRO, region = Region.GLOBAL, subject = "Federal Reserve")
        val move = item(type = NewsItemType.MARKET_MOVE, region = Region.GLOBAL, subject = "Oil")

        val report = ReportAssembler.assemble(date, snapshot, listOf(macro, move))

        assertEquals(listOf(macro, move), report.otherNews)
        assertTrue(report.regionalNews.isEmpty())
        assertTrue(report.companySectorNews.isEmpty())
    }

    @Test
    fun `global company and sector items stay in the company-sector section`() {
        val company = item(type = NewsItemType.COMPANY, region = Region.GLOBAL, subject = "TSMC")

        val report = ReportAssembler.assemble(date, snapshot, listOf(company))

        assertEquals(listOf(company), report.companySectorNews)
        assertTrue(report.otherNews.isEmpty())
    }

    @Test
    fun `summary includes an index quote even without a matching news item`() {
        val summary = ReportAssembler.assemble(date, indexSnapshot(), emptyList()).summary

        assertEquals("S&P 500: 6912.50 (0.61%). Nasdaq Composite: 23950.75 (-0.70%).", summary)
    }

    private fun indexSnapshot() = Snapshot(
        listOf(
            InstrumentQuote(
                ReportInstruments.sp500,
                BigDecimal("6912.50"),
                BigDecimal("42.10"),
                BigDecimal("0.61"),
            ),
            InstrumentQuote(
                ReportInstruments.nasdaqComposite,
                BigDecimal("23950.75"),
                BigDecimal("-168.20"),
                BigDecimal("-0.70"),
            ),
        ),
    )
}
