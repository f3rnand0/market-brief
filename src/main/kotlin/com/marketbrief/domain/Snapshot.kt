package com.marketbrief.domain

import java.math.BigDecimal

enum class AssetClass { EQUITIES, FIXED_INCOME, FX_AND_VOLATILITY, COMMODITIES, CRYPTO }

data class Instrument(val name: String, val assetClass: AssetClass) {
    init {
        require(name.isNotBlank()) { "Instrument name must not be blank" }
    }
}

data class InstrumentQuote(
    val instrument: Instrument,
    val value: BigDecimal,
    val changePercent: BigDecimal,
)

data class Snapshot(val quotes: List<InstrumentQuote>)
