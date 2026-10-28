package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.AssetClass
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote
import com.marketbrief.domain.MarketDataFetcher
import com.marketbrief.domain.Snapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

class CompositeMarketDataFetcher(
    private val primary: QuoteFetcher,
    private val fallbacks: Map<AssetClass, List<QuoteFetcher>>,
) : MarketDataFetcher {

    override suspend fun snapshot(instruments: Set<Instrument>): Snapshot {
        val (primaryQuotes, fallbackQuotes) = coroutineScope {
            val primaryDeferred = async { primary.quotes(instruments) }
            val fallbackDeferred = fallbacks.mapValues { (assetClass, sources) ->
                val group = instruments.filterTo(mutableSetOf()) { it.assetClass == assetClass }
                sources.map { source -> async { source.quotes(group) } }
            }
            primaryDeferred.await() to
                fallbackDeferred.mapValues { (_, deferred) -> deferred.awaitAll() }
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

        val quotes = instruments.mapNotNull(resolved::get)
        val missing = instruments - quotes.mapTo(mutableSetOf()) { it.instrument }
        if (missing.isNotEmpty()) {
            log.warn(
                "Market snapshot incomplete: resolved {} of {} instruments; missing {}",
                quotes.size,
                instruments.size,
                missing.joinToString { it.name },
            )
        }
        return Snapshot(quotes, missing)
    }

    private companion object {
        val log = LoggerFactory.getLogger(CompositeMarketDataFetcher::class.java)
    }
}
