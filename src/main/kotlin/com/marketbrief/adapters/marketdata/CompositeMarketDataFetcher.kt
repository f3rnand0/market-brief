package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.AssetClass
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote
import com.marketbrief.domain.MarketDataFetcher
import com.marketbrief.domain.Snapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

class CompositeMarketDataFetcher(
    private val primary: QuoteFetcher,
    private val fallbacks: Map<AssetClass, List<QuoteFetcher>>,
) : MarketDataFetcher {

    override fun snapshot(instruments: Set<Instrument>): Snapshot = runBlocking {
        val (primaryQuotes, fallbackQuotes) = coroutineScope {
            val primaryDeferred = async { primary.quotes(instruments) }
            val fallbackDeferred = fallbacks.mapValues { (assetClass, sources) ->
                val group = instruments.filterTo(mutableSetOf()) { it.assetClass == assetClass }
                sources.map { source -> async { source.quotes(group) } }
            }
            primaryDeferred.await() to
                fallbackDeferred.mapValues { (_, deferred) -> deferred.map { it.await() } }
        }

        val resolved = primaryQuotes.associateByTo(mutableMapOf<Instrument, InstrumentQuote>()) { it.instrument }
        for ((assetClass, perSource) in fallbackQuotes) {
            for (quotes in perSource) {
                for (quote in quotes) {
                    if (quote.instrument.assetClass == assetClass) {
                        resolved.putIfAbsent(quote.instrument, quote)
                    }
                }
            }
        }

        Snapshot(instruments.mapNotNull(resolved::get))
    }
}
