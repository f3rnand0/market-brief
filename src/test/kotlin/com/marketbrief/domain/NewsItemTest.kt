package com.marketbrief.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class NewsItemTest {

    private val source = Source(articleId = "a1", quotes = listOf("Stocks fell sharply"))

    private fun item(
        type: NewsItemType = NewsItemType.MARKET_MOVE,
        cause: String? = null,
        source: Source = this.source,
    ) = NewsItem(
        type = type,
        region = Region.US,
        subjects = listOf(Subject("S&P 500")),
        assertion = "Stocks fell sharply",
        direction = Direction.DOWN,
        cause = cause,
        metrics = listOf(Metric("-1.2", "%")),
        source = source,
    )

    @Test
    fun `valid news item is constructed`() {
        item(cause = "hawkish Fed comments")
    }

    @Test
    fun `source without quotes is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Source(articleId = "a1", quotes = emptyList())
        }
    }

    @Test
    fun `source with blank article id is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            Source(articleId = " ", quotes = listOf("quote"))
        }
    }

    @Test
    fun `blank assertion is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            item().copy(assertion = "")
        }
    }

    @Test
    fun `cause is only allowed on MARKET_MOVE items`() {
        assertFailsWith<IllegalArgumentException> {
            item(type = NewsItemType.COMPANY, cause = "not allowed here")
        }
    }

    @Test
    fun `blank cause is rejected`() {
        assertFailsWith<IllegalArgumentException> { item(cause = "  ") }
    }

    @Test
    fun `metric requires value and unit`() {
        assertFailsWith<IllegalArgumentException> { Metric("", "%") }
        assertFailsWith<IllegalArgumentException> { Metric("-1.2", "") }
    }
}
