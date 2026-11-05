package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.AssetClass
import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote
import com.marketbrief.domain.ReportInstruments
import com.marketbrief.domain.Snapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompositeMarketDataFetcherTest {

    @Test
    fun `snapshot covers every configured instrument from primary plus fallback fixtures`() = runBlocking {
        val composite = CompositeMarketDataFetcher(
            primary = YahooQuoteFetcher(QuoteFixtures.yahooClient()),
            fallbacks = mapOf(
                AssetClass.FIXED_INCOME to listOf(CnbcQuoteFetcher(QuoteFixtures.cnbcClient())),
            ),
        )

        val snapshot = composite.snapshot(ReportInstruments.all)
        val expectedSnapshot = QuoteFixtures.expectedSnapshot("2026-10-28")

        assertSnapshotEquals(expectedSnapshot, snapshot)
    }

    @Test
    fun `primary quote wins over a fallback quote for the same instrument`() = runBlocking {
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
                return listOf(InstrumentQuote(instrument, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO))
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

    @Test
    fun `unresolved instruments are omitted from quotes and recorded as missing`() = runBlocking {
        val composite = CompositeMarketDataFetcher(
            primary = fetcherOf(ReportInstruments.sp500, "100", "1.00"),
            fallbacks = emptyMap(),
        )

        val snapshot = composite.snapshot(setOf(ReportInstruments.sp500, ReportInstruments.nasdaqComposite))

        assertEquals(setOf(ReportInstruments.sp500), snapshot.quotes.map { it.instrument }.toSet())
        assertEquals(setOf(ReportInstruments.nasdaqComposite), snapshot.missing)
    }

    private fun fetcherOf(instrument: Instrument, value: String, changePercent: String): QuoteFetcher =
        object : QuoteFetcher {
            override suspend fun quotes(instruments: Set<Instrument>): List<InstrumentQuote> =
                if (instrument in instruments) {
                    listOf(
                        InstrumentQuote(instrument, BigDecimal(value), BigDecimal.ZERO, BigDecimal(changePercent)),
                    )
                } else {
                    emptyList()
                }
        }

    private fun assertSnapshotEquals(expected: Snapshot, actual: Snapshot) {
        assertEquals(expected.missing, actual.missing)
        val expectedQuotes = expected.quotes.associateBy { it.instrument }
        val actualQuotes = actual.quotes.associateBy { it.instrument }
        assertEquals(expectedQuotes.keys, actualQuotes.keys)
        expectedQuotes.forEach { (instrument, expectedQuote) ->
            val actualQuote = actualQuotes.getValue(instrument)
            assertEquals(0, expectedQuote.value.compareTo(actualQuote.value), "$instrument value")
            assertEquals(0, expectedQuote.change.compareTo(actualQuote.change), "$instrument change")
            assertEquals(
                0,
                expectedQuote.changePercent.compareTo(actualQuote.changePercent),
                "$instrument percent change",
            )
        }
    }
}
