package com.marketbrief.domain

import java.math.BigDecimal

enum class AssetClass { EQUITIES, FIXED_INCOME, FX_AND_VOLATILITY, COMMODITIES, CRYPTO }

/** A tracked market instrument. */
data class Instrument(val name: String, val assetClass: AssetClass) {
    init {
        require(name.isNotBlank()) { "Instrument name must not be blank" }
    }
}

/** One instrument's numbers: level and day-over-day change in percent. */
data class InstrumentQuote(
    val instrument: Instrument,
    val value: BigDecimal,
    val changePercent: BigDecimal,
)

/**
 * The structured market-numbers section. Values come from a market-data
 * API, never from article prose.
 */
data class Snapshot(val quotes: List<InstrumentQuote>)
