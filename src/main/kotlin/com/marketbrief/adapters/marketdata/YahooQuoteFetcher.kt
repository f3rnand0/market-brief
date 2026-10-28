package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class YahooQuoteFetcher(
    private val client: HttpClient,
    private val symbols: Map<Instrument, String> = YahooSymbols.byInstrument,
    private val baseUrl: String = "https://query1.finance.yahoo.com",
    private val json: Json = Json { ignoreUnknownKeys = true },
) : QuoteFetcher {

    override suspend fun quotes(instruments: Set<Instrument>): List<InstrumentQuote> = coroutineScope {
        instruments
            .mapNotNull { instrument -> symbols[instrument]?.let { symbol -> async { quote(instrument, symbol) } } }
            .awaitAll()
            .filterNotNull()
    }

    private suspend fun quote(instrument: Instrument, symbol: String): InstrumentQuote? = try {
        val body = QuoteRequestPolicy.bodyOrNull("Yahoo", symbol, log) {
            client.get("$baseUrl/v8/finance/chart") {
                url { appendPathSegments(symbol) }
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (compatible; market-brief/0.1)")
                parameter("range", "1d")
                parameter("interval", "1d")
            }
        } ?: return null
        toQuote(instrument, json.decodeFromString<YahooChartResponse>(body))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.warn("Yahoo response for {} could not be parsed", symbol, e)
        null
    }

    private fun toQuote(instrument: Instrument, response: YahooChartResponse): InstrumentQuote? {
        val meta = response.chart.result?.firstOrNull()?.meta
        if (meta == null) {
            log.warn("Yahoo response for {} has no chart result metadata", instrument.name)
            return null
        }
        val current = meta.regularMarketPrice
        if (current == null) {
            log.warn("Yahoo response for {} has no regularMarketPrice", instrument.name)
            return null
        }
        val previous = meta.chartPreviousClose ?: meta.previousClose
        if (previous == null) {
            log.warn("Yahoo response for {} has no previous close", instrument.name)
            return null
        }
        val price = BigDecimal.valueOf(current)
        val changePercent = percentChange(BigDecimal.valueOf(previous), price)
        if (changePercent == null) {
            log.warn("Yahoo previous close for {} is zero", instrument.name)
            return null
        }
        return InstrumentQuote(instrument, price, price.subtract(BigDecimal.valueOf(previous)), changePercent)
    }

    private companion object {
        val log = LoggerFactory.getLogger(YahooQuoteFetcher::class.java)
    }
}

@Serializable
private data class YahooChartResponse(val chart: YahooChart)

@Serializable
private data class YahooChart(val result: List<YahooChartResult>? = null)

@Serializable
private data class YahooChartResult(val meta: YahooMeta? = null)

@Serializable
private data class YahooMeta(
    val regularMarketPrice: Double? = null,
    val chartPreviousClose: Double? = null,
    val previousClose: Double? = null,
)
