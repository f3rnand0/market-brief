package com.marketbrief.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportInstrumentsTest {

    @Test
    fun `catalog covers the full instrument set by asset class`() {
        assertEquals(
            mapOf(
                AssetClass.EQUITIES to 8,
                AssetClass.FIXED_INCOME to 6,
                AssetClass.FX_AND_VOLATILITY to 3,
                AssetClass.COMMODITIES to 5,
                AssetClass.CRYPTO to 2,
            ),
            ReportInstruments.all.groupingBy { it.assetClass }.eachCount(),
        )
    }

    @Test
    fun `summary index aliases match common subject names`() {
        assertEquals(ReportInstruments.sp500, ReportInstruments.summaryInstrumentFor("S&P500"))
        assertEquals(ReportInstruments.sp500, ReportInstruments.summaryInstrumentFor("S&P 500 Index"))
        assertEquals(ReportInstruments.sp500, ReportInstruments.summaryInstrumentFor("^GSPC"))
        assertEquals(ReportInstruments.nasdaqComposite, ReportInstruments.summaryInstrumentFor("Nasdaq"))
        assertEquals(ReportInstruments.nasdaqComposite, ReportInstruments.summaryInstrumentFor("^IXIC"))
        assertNull(ReportInstruments.summaryInstrumentFor("Apple"))
    }
}
