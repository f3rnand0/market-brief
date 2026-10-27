package com.marketbrief.domain

import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `summary index names match the catalog`() {
        val names = ReportInstruments.all.map { it.name }
        assertEquals(
            listOf("S&P 500", "Nasdaq Composite"),
            names.filter { it == "S&P 500" || it == "Nasdaq Composite" },
        )
    }
}
