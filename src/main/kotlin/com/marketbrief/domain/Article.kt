package com.marketbrief.domain

import java.time.LocalDate
import java.util.Locale

data class Article(
    val id: String,
    val headline: String,
    val body: String,
    val outlet: String,
    val url: String,
    val publishedAt: LocalDate,
) {
    init {
        require(id.isNotBlank()) { "Article id must not be blank" }
        require(headline.isNotBlank()) { "Article headline must not be blank" }
        require(body.isNotBlank()) { "Article body must not be blank" }
    }

    fun containsQuote(quote: String): Boolean {
        val normalizedQuote = quote.normalizedForQuoteMatch()
        return normalizedQuote.isNotEmpty() &&
            (
                normalizedTextBody().contains(normalizedQuote) ||
                    headline.normalizedForQuoteMatch().contains(normalizedQuote)
                )
    }

    private fun normalizedTextBody(): String = body.normalizedForQuoteMatch()

    private fun String.normalizedForQuoteMatch(): String = trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
