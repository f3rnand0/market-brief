package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class CnbcQuoteFetcher(
    private val client: HttpClient,
    private val symbols: Map<Instrument, String> = CnbcSymbols.byInstrument,
    private val baseUrl: String = "https://quote.cnbc.com",
    private val json: Json = Json { ignoreUnknownKeys = true },
) : QuoteFetcher {

    override suspend fun quotes(instruments: Set<Instrument>): List<InstrumentQuote> {
        val bySymbol = instruments
            .mapNotNull { instrument -> symbols[instrument]?.let { it to instrument } }
            .toMap()
        if (bySymbol.isEmpty()) return emptyList()

        return try {
            val response = client.get("$baseUrl/quote-html-webservice/restQuote/symbolType/symbol") {
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (compatible; market-brief/0.1)")
                parameter("symbols", bySymbol.keys.joinToString("|"))
                parameter("requestMethod", "itv")
                parameter("noform", "1")
                parameter("partnerId", "2")
                parameter("output", "json")
            }
            if (!response.status.isSuccess()) {
                log.debug("CNBC returned {} for {}", response.status, bySymbol.keys)
                emptyList()
            } else {
                toQuotes(json.decodeFromString<CnbcQuoteResponse>(response.bodyAsText()), bySymbol)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("CNBC request failed for {}", bySymbol.keys, e)
            emptyList()
        }
    }

    private fun toQuotes(
        response: CnbcQuoteResponse,
        bySymbol: Map<String, Instrument>,
    ): List<InstrumentQuote> =
        response.result?.quotes.orEmpty().mapNotNull { quote ->
            val instrument = bySymbol[quote.symbol] ?: return@mapNotNull null
            val latest = quote.last.asPercent() ?: return@mapNotNull null
            val previous = quote.previousClose.asPercent() ?: return@mapNotNull null
            percentChange(previous, latest)?.let { InstrumentQuote(instrument, latest, it) }
        }

    private fun String?.asPercent(): BigDecimal? =
        this?.removeSuffix("%")?.trim()?.toBigDecimalOrNull()

    private companion object {
        val log = LoggerFactory.getLogger(CnbcQuoteFetcher::class.java)
    }
}

@Serializable
private data class CnbcQuoteResponse(
    @SerialName("FormattedQuoteResult") val result: CnbcQuoteResult? = null,
)

@Serializable
private data class CnbcQuoteResult(
    @SerialName("FormattedQuote") val quotes: List<CnbcQuote> = emptyList(),
)

@Serializable
private data class CnbcQuote(
    val symbol: String = "",
    val last: String? = null,
    @SerialName("previous_day_closing") val previousClose: String? = null,
)
