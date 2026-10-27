package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
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
        val body = withTimeout(REQUEST_TIMEOUT_MILLIS) {
            val response = client.get("$baseUrl/v8/finance/chart") {
                url { appendPathSegments(symbol) }
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (compatible; market-brief/0.1)")
                parameter("range", "1d")
                parameter("interval", "1d")
            }
            if (!response.status.isSuccess()) {
                log.debug("Yahoo returned {} for {}", response.status, symbol)
                return@withTimeout null
            }
            response.bodyAsText()
        } ?: return null
        toQuote(instrument, json.decodeFromString<YahooChartResponse>(body))
    } catch (e: TimeoutCancellationException) {
        log.warn("Yahoo request timed out for {}", symbol)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.warn("Yahoo request failed for {}", symbol, e)
        null
    }

    private fun toQuote(instrument: Instrument, response: YahooChartResponse): InstrumentQuote? {
        val meta = response.chart.result?.firstOrNull()?.meta ?: return null
        val current = meta.regularMarketPrice ?: return null
        val previous = meta.chartPreviousClose ?: meta.previousClose ?: return null
        val price = BigDecimal.valueOf(current)
        val changePercent = percentChange(BigDecimal.valueOf(previous), price) ?: return null
        return InstrumentQuote(instrument, price, price.subtract(BigDecimal.valueOf(previous)), changePercent)
    }

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 15_000L
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
