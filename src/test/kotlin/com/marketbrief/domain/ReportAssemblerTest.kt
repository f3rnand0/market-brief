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
    fun `summary leads with S&P and Nasdaq moves and their causes`() {
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

        val summary = ReportAssembler.assemble(date, snapshot, items).summary

        assertTrue(summary.contains("S&P 500 fell 1.2%"))
        assertTrue(summary.contains("Caused by: hawkish Fed comments."))
        assertTrue(summary.contains("Nasdaq dropped 2%"))
        assertTrue(!summary.contains("Apple"))
    }

    @Test
    fun `summary states when no index moves exist`() {
        val summary = ReportAssembler.assemble(date, snapshot, emptyList()).summary
        assertEquals("No major index moves.", summary)
    }
}
