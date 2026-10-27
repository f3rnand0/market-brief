package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.ReportInstruments
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CnbcQuoteFetcherTest {

    private val cnbc = CnbcQuoteFetcher(QuoteFixtures.cnbcClient())

    @Test
    fun `maps fixed-income yields from recorded quotes`() = runBlocking {
        val quotes = cnbc.quotes(ReportInstruments.all).associateBy { it.instrument }

        assertEquals(CnbcSymbols.byInstrument.keys, quotes.keys)
        assertQuote(quotes, ReportInstruments.us10y, "4.995", "0.18", "0.009")
        assertQuote(quotes, ReportInstruments.germany10y, "3.3585", "0.09")
        assertQuote(quotes, ReportInstruments.france10y, "4.9236", "0.32")
        assertQuote(quotes, ReportInstruments.uk10y, "5.3496", "0.13")
        assertQuote(quotes, ReportInstruments.japan10y, "2.819", "1.11")
        assertQuote(quotes, ReportInstruments.china10y, "1.633", "0.00")
    }

    @Test
    fun `quotes without two prices are skipped`() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        """
                        {"FormattedQuoteResult":{"FormattedQuote":[
                          {"symbol":"DE10Y-DE","last":"3.35%","previous_day_closing":"3.30%"},
                          {"symbol":"FR10Y-FR","last":"4.90%"}
                        ]}}
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }

        val quotes = CnbcQuoteFetcher(client)
            .quotes(setOf(ReportInstruments.germany10y, ReportInstruments.france10y))
            .associateBy { it.instrument }

        assertTrue(ReportInstruments.france10y !in quotes)
        assertQuote(quotes, ReportInstruments.germany10y, "3.35", "1.52")
    }
}
