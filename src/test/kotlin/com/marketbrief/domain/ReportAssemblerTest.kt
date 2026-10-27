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
            item(region = Region.US, subject = "Fed"),
            item(region = Region.CHINA, subject = "PBOC"),
            item(region = Region.US, subject = "Treasuries"),
            item(region = Region.GLOBAL, subject = "Oil"),
        )

        val regional = ReportAssembler.assemble(date, snapshot, items).regionalNews

        assertEquals(setOf(Region.US, Region.CHINA), regional.keys)
        assertEquals(2, regional[Region.US]!!.size)
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
            item(type = NewsItemType.COMPANY, subject = "Apple"), // not a move: stays out
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
    fun `summary states when the snapshot has no index moves`() {
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
