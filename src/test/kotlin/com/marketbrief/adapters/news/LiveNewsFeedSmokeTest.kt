package com.marketbrief.adapters.news

import com.marketbrief.domain.DayWindow
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.junit.jupiter.api.Tag
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

class LiveNewsFeedSmokeTest {
    @Test
    @Tag("live")
    fun `BBC business RSS returns publisher articles for the previous day`() {
        val client = HttpClient(CIO)
        try {
            val articles = RssNewsSource(client, listOf("https://feeds.bbci.co.uk/news/business/rss.xml"))
                .fetch(DayWindow(LocalDate.now().minusDays(1)))

            assertTrue(articles.isNotEmpty(), "BBC feed produced no fetchable articles for yesterday")
            assertTrue(articles.all { it.body.split(Regex("\\s+")).size >= 80 })
        } finally {
            client.close()
        }
    }
}
