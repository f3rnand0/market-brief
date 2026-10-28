package com.marketbrief.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SnapshotTest {

    @Test
    fun `missing instruments default to empty`() {
        assertTrue(Snapshot(emptyList()).missing.isEmpty())
    }

    @Test
    fun `an instrument cannot be both quoted and missing`() {
        val quote = InstrumentQuote(ReportInstruments.sp500, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)

        assertFailsWith<IllegalArgumentException> {
            Snapshot(listOf(quote), setOf(ReportInstruments.sp500))
        }
    }
}
