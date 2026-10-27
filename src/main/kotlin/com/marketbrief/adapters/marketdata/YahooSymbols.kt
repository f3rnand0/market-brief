package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.Instrument
import com.marketbrief.domain.ReportInstruments

object YahooSymbols {
    val byInstrument: Map<Instrument, String> = mapOf(
        ReportInstruments.sp500 to "^GSPC",
        ReportInstruments.nasdaqComposite to "^IXIC",
        ReportInstruments.stoxxEurope600 to "^STOXX",
        ReportInstruments.ftse100 to "^FTSE",
        ReportInstruments.dax40 to "^GDAXI",
        ReportInstruments.nikkei225 to "^N225",
        ReportInstruments.shanghaiComposite to "000001.SS",
        ReportInstruments.hangSeng to "^HSI",
        ReportInstruments.us10y to "^TNX",
        ReportInstruments.eurUsd to "EURUSD=X",
        ReportInstruments.usdCny to "CNY=X",
        ReportInstruments.vix to "^VIX",
        ReportInstruments.gold to "GC=F",
        ReportInstruments.silver to "SI=F",
        ReportInstruments.copper to "HG=F",
        ReportInstruments.brent to "BZ=F",
        ReportInstruments.wti to "CL=F",
        ReportInstruments.bitcoin to "BTC-USD",
        ReportInstruments.ethereum to "ETH-USD",
    )
}
