package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.ReportInstruments
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YahooQuoteFetcherTest {

    private val yahoo = YahooQuoteFetcher(QuoteFixtures.yahooClient())

    @Test
    fun `maps the instruments Yahoo covers from recorded chart responses`() = runBlocking {
        val quotes = yahoo.quotes(YahooSymbols.byInstrument.keys).associateBy { it.instrument }

        assertEquals(YahooSymbols.byInstrument.keys, quotes.keys)
        assertQuote(quotes, ReportInstruments.sp500, "6912.50", "0.61")
        assertQuote(quotes, ReportInstruments.nasdaqComposite, "23950.75", "-0.70")
        assertQuote(quotes, ReportInstruments.us10y, "4.995", "0.18", "0.009")
        assertQuote(quotes, ReportInstruments.eurUsd, "1.1642", "0.28")
        assertQuote(quotes, ReportInstruments.vix, "16.85", "-3.27")
        assertQuote(quotes, ReportInstruments.bitcoin, "112450.00", "1.49")
        assertQuote(quotes, ReportInstruments.ethereum, "4085.50", "-0.86")
    }

    @Test
    fun `requests the chart endpoint with the expected query and user agent`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val fetcher = YahooQuoteFetcher(QuoteFixtures.yahooClient(requests))

        fetcher.quotes(setOf(ReportInstruments.sp500))

        val request = requests.single()
        assertEquals("query1.finance.yahoo.com", request.url.host)
        assertEquals("^GSPC", request.url.encodedPath.substringAfterLast('/').decodeURLPart())
        assertEquals("1d", request.url.parameters["range"])
        assertEquals("1d", request.url.parameters["interval"])
        assertEquals("Mozilla/5.0 (compatible; market-brief/0.1)", request.headers[HttpHeaders.UserAgent])
    }

    @Test
    fun `instruments Yahoo does not cover are omitted, not fatal`() = runBlocking {
        val quotes = yahoo.quotes(ReportInstruments.all)

        assertEquals(YahooSymbols.byInstrument.keys, quotes.map { it.instrument }.toSet())
        assertTrue(
            quotes.none { it.instrument == ReportInstruments.germany10y },
            "non-US yields must come from the fallback",
        )
    }

    @Test
    fun `malformed payload is skipped while other quotes survive`() = runBlocking {
        val client = QuoteFixtures.yahooClient(bodyFor = { symbol ->
            when (symbol) {
                "^GSPC" -> "{not json"
                "^VIX" -> chart("regularMarketPrice" to "16.85", "chartPreviousClose" to "17.42")
                else -> null
            }
        })

        val quotes = YahooQuoteFetcher(client)
            .quotes(setOf(ReportInstruments.sp500, ReportInstruments.vix))
            .associateBy { it.instrument }

        assertEquals(setOf(ReportInstruments.vix), quotes.keys)
        assertQuote(quotes, ReportInstruments.vix, "16.85", "-3.27")
    }

    @Test
    fun `previousClose is used when chartPreviousClose is absent`() = runBlocking {
        val client = QuoteFixtures.yahooClient(bodyFor = { symbol ->
            if (symbol == "^GSPC") chart("regularMarketPrice" to "100.0", "previousClose" to "98.0") else null
        })

        val quotes = YahooQuoteFetcher(client)
            .quotes(setOf(ReportInstruments.sp500))
            .associateBy { it.instrument }

        assertQuote(quotes, ReportInstruments.sp500, "100.0", "2.04")
    }

    private fun chart(vararg meta: Pair<String, String>): String =
        """{"chart":{"result":[{"meta":{${meta.joinToString(",") { (key, value) -> "\"$key\":$value" }}}}]}}"""
}
