package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

object QuoteFixtures {

    private val yahooChartQuotes: JsonObject = Json
        .parseToJsonElement(resource("/fixtures/yahoo/chart-quotes.json"))
        .jsonObject

    fun yahooClient(
        requests: MutableList<HttpRequestData>? = null,
        bodyFor: (symbol: String) -> String? = { yahooChartQuotes[it]?.toString() },
    ): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                requests?.add(request)
                val symbol = request.url.encodedPath
                    .substringAfter("/v8/finance/chart/")
                    .decodeURLPart()
                val body = bodyFor(symbol)
                if (body == null) {
                    respond("", HttpStatusCode.NotFound)
                } else {
                    respond(
                        body,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }
    }

    fun cnbcClient(requests: MutableList<HttpRequestData>? = null): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                requests?.add(request)
                respond(
                    resource("/fixtures/cnbc/quotes.json"),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }
    }

    private fun resource(path: String): String = checkNotNull(readResourceOrNull(path)) { "missing fixture $path" }

    private fun readResourceOrNull(path: String): String? = QuoteFixtures::class.java.getResourceAsStream(path)
        ?.bufferedReader()
        ?.use { it.readText() }
}

internal fun assertQuote(
    quotes: Map<Instrument, InstrumentQuote>,
    instrument: Instrument,
    value: String,
    changePercent: String,
    absoluteChange: String? = null,
) {
    val quote = assertNotNull(quotes[instrument], "missing quote for ${instrument.name}")
    assertEquals(0, quote.value.compareTo(BigDecimal(value)), "value for ${instrument.name}")
    assertEquals(0, quote.changePercent.compareTo(BigDecimal(changePercent)), "percent change for ${instrument.name}")
    if (absoluteChange != null) {
        assertEquals(0, quote.change.compareTo(BigDecimal(absoluteChange)), "absolute change for ${instrument.name}")
    }
}
