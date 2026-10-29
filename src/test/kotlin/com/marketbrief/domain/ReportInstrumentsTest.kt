package com.marketbrief.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportInstrumentsTest {

    @Test
    fun `catalog covers every PRD instrument by asset class`() {
        assertEquals(
            mapOf(
                AssetClass.EQUITIES to setOf(
                    "S&P 500",
                    "Nasdaq Composite",
                    "STOXX Europe 600",
                    "FTSE 100",
                    "DAX 40",
                    "Nikkei 225",
                    "Shanghai Composite",
                    "Hang Seng",
                ),
                AssetClass.FIXED_INCOME to setOf(
                    "US 10Y",
                    "Germany 10Y",
                    "France 10Y",
                    "UK 10Y",
                    "Japan 10Y",
                    "China 10Y",
                ),
                AssetClass.FX_AND_VOLATILITY to setOf("EUR/USD", "USD/CNY", "VIX"),
                AssetClass.COMMODITIES to setOf("Gold", "Silver", "Copper", "Brent", "WTI"),
                AssetClass.CRYPTO to setOf("Bitcoin", "Ethereum"),
            ),
            ReportInstruments.all.groupBy({ it.assetClass }, { it.name }).mapValues { it.value.toSet() },
        )
    }

    @Test
    fun `summary index aliases match common subject names`() {
        assertEquals(ReportInstruments.sp500, ReportInstruments.summaryInstrumentFor("S&P500"))
        assertEquals(ReportInstruments.sp500, ReportInstruments.summaryInstrumentFor("S&P 500 Index"))
        assertEquals(ReportInstruments.nasdaqComposite, ReportInstruments.summaryInstrumentFor("Nasdaq"))
        assertNull(ReportInstruments.summaryInstrumentFor("Apple"))
    }

    @Test
    fun `every summary index matches its own display name`() {
        for (instrument in ReportInstruments.summaryIndices) {
            assertEquals(instrument, ReportInstruments.summaryInstrumentFor(instrument.name))
        }
    }

    @Test
    fun `ticker symbols are not matched because adapters own them`() {
        assertNull(ReportInstruments.summaryInstrumentFor("^GSPC"))
        assertNull(ReportInstruments.summaryInstrumentFor("^IXIC"))
    }
}
