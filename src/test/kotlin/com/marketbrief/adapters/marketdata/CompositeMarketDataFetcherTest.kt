package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.AssetClass
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote
import com.marketbrief.domain.ReportInstruments
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class CompositeMarketDataFetcherTest {

    @Test
    fun `snapshot covers every configured instrument from primary plus fallback fixtures`() {
        val composite = CompositeMarketDataFetcher(
            primary = YahooQuoteFetcher(QuoteFixtures.yahooClient()),
            fallbacks = mapOf(
                AssetClass.FIXED_INCOME to listOf(CnbcQuoteFetcher(QuoteFixtures.cnbcClient())),
            ),
        )

        val snapshot = composite.snapshot(ReportInstruments.all)
        val quotes = snapshot.quotes.associateBy { it.instrument }

        assertEquals(ReportInstruments.all, quotes.keys)
        assertQuote(quotes, ReportInstruments.sp500, "6912.50", "0.61")
        assertQuote(quotes, ReportInstruments.us10y, "4.995", "0.18")
        assertQuote(quotes, ReportInstruments.germany10y, "3.3585", "0.09")
        assertQuote(quotes, ReportInstruments.china10y, "1.633", "0.00")
        assertQuote(quotes, ReportInstruments.bitcoin, "112450.00", "1.49")
    }

    @Test
    fun `primary quote wins over a fallback quote for the same instrument`() {
        val instrument = ReportInstruments.us10y
        val composite = CompositeMarketDataFetcher(
            primary = fetcherOf(instrument, "4.20", "3.70"),
            fallbacks = mapOf(AssetClass.FIXED_INCOME to listOf(fetcherOf(instrument, "4.12", "1.73"))),
        )

        val quotes = composite.snapshot(setOf(instrument)).quotes.associateBy { it.instrument }

        assertQuote(quotes, instrument, "4.20", "3.70")
    }

    @Test
    fun `primary and fallbacks are queried concurrently`() = runBlocking {
        val started = AtomicInteger()
        val allStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        fun gated(instrument: Instrument): QuoteFetcher = object : QuoteFetcher {
            override suspend fun quotes(instruments: Set<Instrument>): List<InstrumentQuote> {
                if (started.incrementAndGet() == 2) allStarted.complete(Unit)
                release.await()
                return listOf(InstrumentQuote(instrument, BigDecimal.ONE, BigDecimal.ZERO))
            }
        }

        val primaryInstrument = ReportInstruments.sp500
        val fallbackInstrument = ReportInstruments.us10y
        val composite = CompositeMarketDataFetcher(
            primary = gated(primaryInstrument),
            fallbacks = mapOf(AssetClass.FIXED_INCOME to listOf(gated(fallbackInstrument))),
        )

        withTimeout(5_000) {
            val snapshot = async(Dispatchers.Default) {
                composite.snapshot(setOf(primaryInstrument, fallbackInstrument))
            }
            allStarted.await()
            release.complete(Unit)
            assertEquals(2, snapshot.await().quotes.size)
        }
    }

    private fun fetcherOf(instrument: Instrument, value: String, change: String): QuoteFetcher =
        object : QuoteFetcher {
            override suspend fun quotes(instruments: Set<Instrument>): List<InstrumentQuote> =
                if (instrument in instruments) {
                    listOf(InstrumentQuote(instrument, BigDecimal(value), BigDecimal(change)))
                } else {
                    emptyList()
                }
        }
}
