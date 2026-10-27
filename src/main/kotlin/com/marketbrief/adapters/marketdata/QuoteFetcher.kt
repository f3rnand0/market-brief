package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote

interface QuoteFetcher {
    suspend fun quotes(instruments: Set<Instrument>): List<InstrumentQuote>
}
