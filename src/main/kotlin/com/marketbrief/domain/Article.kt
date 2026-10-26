package com.marketbrief.domain

import java.time.LocalDate

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
}
