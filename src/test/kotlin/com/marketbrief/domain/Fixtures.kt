package com.marketbrief.domain

import java.time.LocalDate

fun article(id: String, outlet: String = "Reuters") = Article(
    id = id,
    headline = "Headline $id",
    body = "Body of $id",
    outlet = outlet,
    url = "https://example.com/$id",
    publishedAt = LocalDate.of(2026, 10, 25),
)

fun item(
    type: NewsItemType = NewsItemType.COMPANY,
    region: Region = Region.US,
    subject: String = "Subject",
    assertion: String = "Something happened",
    cause: String? = null,
) = NewsItem(
    type = type,
    region = region,
    subjects = listOf(Subject(subject)),
    assertion = assertion,
    direction = if (type == NewsItemType.MARKET_MOVE) Direction.DOWN else null,
    cause = cause,
    metrics = emptyList(),
    source = Source(articleId = "a1", quotes = listOf("supporting quote")),
)
