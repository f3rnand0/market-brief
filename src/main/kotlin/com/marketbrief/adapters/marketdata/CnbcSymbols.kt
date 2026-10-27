package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.Instrument
import com.marketbrief.domain.ReportInstruments

object CnbcSymbols {
    val byInstrument: Map<Instrument, String> = mapOf(
        ReportInstruments.us10y to "US10Y",
        ReportInstruments.germany10y to "DE10Y-DE",
        ReportInstruments.france10y to "FR10Y-FR",
        ReportInstruments.uk10y to "GB10Y-GB",
        ReportInstruments.japan10y to "JP10Y-JP",
        ReportInstruments.china10y to "CN10Y-CN",
    )
}
