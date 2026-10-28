package com.marketbrief.domain

object ReportInstruments {
    val sp500 = Instrument("S&P 500", AssetClass.EQUITIES)
    val nasdaqComposite = Instrument("Nasdaq Composite", AssetClass.EQUITIES)
    val stoxxEurope600 = Instrument("STOXX Europe 600", AssetClass.EQUITIES)
    val ftse100 = Instrument("FTSE 100", AssetClass.EQUITIES)
    val dax40 = Instrument("DAX 40", AssetClass.EQUITIES)
    val nikkei225 = Instrument("Nikkei 225", AssetClass.EQUITIES)
    val shanghaiComposite = Instrument("Shanghai Composite", AssetClass.EQUITIES)
    val hangSeng = Instrument("Hang Seng", AssetClass.EQUITIES)

    val us10y = Instrument("US 10Y", AssetClass.FIXED_INCOME)
    val germany10y = Instrument("Germany 10Y", AssetClass.FIXED_INCOME)
    val france10y = Instrument("France 10Y", AssetClass.FIXED_INCOME)
    val uk10y = Instrument("UK 10Y", AssetClass.FIXED_INCOME)
    val japan10y = Instrument("Japan 10Y", AssetClass.FIXED_INCOME)
    val china10y = Instrument("China 10Y", AssetClass.FIXED_INCOME)

    val eurUsd = Instrument("EUR/USD", AssetClass.FX_AND_VOLATILITY)
    val usdCny = Instrument("USD/CNY", AssetClass.FX_AND_VOLATILITY)
    val vix = Instrument("VIX", AssetClass.FX_AND_VOLATILITY)

    val gold = Instrument("Gold", AssetClass.COMMODITIES)
    val silver = Instrument("Silver", AssetClass.COMMODITIES)
    val copper = Instrument("Copper", AssetClass.COMMODITIES)
    val brent = Instrument("Brent", AssetClass.COMMODITIES)
    val wti = Instrument("WTI", AssetClass.COMMODITIES)

    val bitcoin = Instrument("Bitcoin", AssetClass.CRYPTO)
    val ethereum = Instrument("Ethereum", AssetClass.CRYPTO)

    val summaryIndices: List<Instrument> = listOf(sp500, nasdaqComposite)

    private val summaryIndexAliases: Map<Instrument, Set<String>> = mapOf(
        sp500 to setOf("S&P 500", "S&P500", "S&P 500 Index", "^GSPC"),
        nasdaqComposite to setOf("Nasdaq Composite", "Nasdaq", "Nasdaq Comp", "^IXIC"),
    )

    fun summaryInstrumentFor(subjectName: String): Instrument? {
        val normalized = normalize(subjectName)
        return summaryIndexAliases.entries
            .firstOrNull { (_, aliases) -> aliases.any { normalize(it) == normalized } }
            ?.key
    }

    private fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    val all: Set<Instrument> = linkedSetOf(
        sp500, nasdaqComposite, stoxxEurope600, ftse100, dax40, nikkei225,
        shanghaiComposite, hangSeng,
        us10y, germany10y, france10y, uk10y, japan10y, china10y,
        eurUsd, usdCny, vix,
        gold, silver, copper, brent, wti,
        bitcoin, ethereum,
    )
}
