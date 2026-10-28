package com.marketbrief.adapters.marketdata

import com.marketbrief.domain.Instrument
import com.marketbrief.domain.InstrumentQuote
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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

        val body = QuoteRequestPolicy.bodyOrNull("CNBC", bySymbol.keys, log) {
            client.get("$baseUrl/quote-html-webservice/restQuote/symbolType/symbol") {
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (compatible; market-brief/0.1)")
                parameter("symbols", bySymbol.keys.joinToString("|"))
                parameter("requestMethod", "itv")
                parameter("noform", "1")
                parameter("partnerId", "2")
                parameter("output", "json")
            }
        } ?: return emptyList()
        return parseQuotes(body, bySymbol)
    }

    private fun parseQuotes(body: String, bySymbol: Map<String, Instrument>): List<InstrumentQuote> {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            log.warn("CNBC returned malformed JSON", e)
            return emptyList()
        }
        val rawQuotes = try {
            root["FormattedQuoteResult"]
                ?.jsonObject
                ?.get("FormattedQuote")
                ?.jsonArray
        } catch (e: Exception) {
            log.warn("CNBC returned an unexpected payload shape", e)
            return emptyList()
        } ?: return emptyList()
        return rawQuotes.mapNotNull { element ->
            val quote = try {
                json.decodeFromJsonElement<CnbcQuote>(element)
            } catch (e: Exception) {
                log.warn("Skipping malformed CNBC quote", e)
                return@mapNotNull null
            }
            toQuote(quote, bySymbol)
        }
    }

    private fun toQuote(quote: CnbcQuote, bySymbol: Map<String, Instrument>): InstrumentQuote? {
        val instrument = bySymbol[quote.symbol]
        if (instrument == null) {
            log.warn("CNBC returned an unmapped symbol {}", quote.symbol)
            return null
        }
        val latest = quote.last.asPercent()
        if (latest == null) {
            log.warn("CNBC quote for {} has no parseable last price", instrument.name)
            return null
        }
        val previous = quote.previousClose.asPercent()
        if (previous == null) {
            log.warn("CNBC quote for {} has no parseable previous close", instrument.name)
            return null
        }
        val changePercent = percentChange(previous, latest)
        if (changePercent == null) {
            log.warn("CNBC previous close for {} is zero", instrument.name)
            return null
        }
        return InstrumentQuote(instrument, latest, latest.subtract(previous), changePercent)
    }

    private fun String?.asPercent(): BigDecimal? = this?.removeSuffix("%")?.trim()?.toBigDecimalOrNull()

    private companion object {
        val log = LoggerFactory.getLogger(CnbcQuoteFetcher::class.java)
    }
}

@Serializable
private data class CnbcQuote(
    val symbol: String = "",
    val last: String? = null,
    @SerialName("previous_day_closing") val previousClose: String? = null,
)
