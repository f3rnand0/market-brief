package com.marketbrief.adapters.news

import com.marketbrief.domain.Article
import com.marketbrief.domain.DayWindow
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NewsFeedSourceTest {
    @Test
    fun `parses recorded RSS and fetches only publisher text from requested day`() {
        val body = List(12) { "Investors reviewed the latest market development and its effect on trading." }
            .joinToString(" ")
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                "/feed" -> respond(fixture("market-rss.xml"))
                "/market-rally" -> respond("<html><article><p>$body</p></article></html>")
                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val articles = RssNewsSource(client, listOf("https://feed.example/feed"))
            .fetch(DayWindow(LocalDate.of(2026, 11, 3)))

        assertEquals(1, articles.size)
        assertEquals("Markets rally after central bank decision", articles.single().headline)
        assertEquals("Example Publisher", articles.single().outlet)
        assertEquals("https://93.184.216.34/market-rally", articles.single().url)
        assertEquals(body, articles.single().body)
    }

    @Test
    fun `partial RSS feed failures retain articles from successful feeds`() {
        val body = List(12) { "Investors reviewed the latest market development and its effect on trading." }
            .joinToString(" ")
        val client = mockClient { request ->
            when (request.url.host) {
                "failed.example" -> respond("", HttpStatusCode.ServiceUnavailable)
                "feed.example" -> respond(fixture("market-rss.xml"))
                else -> respond("<html><article><p>$body</p></article></html>")
            }
        }

        val articles = RssNewsSource(
            client,
            listOf("https://failed.example/feed", "https://feed.example/feed"),
        ).fetch(DayWindow(LocalDate.of(2026, 11, 3)))

        assertEquals(1, articles.size)
    }

    @Test
    fun `all RSS feed failures are surfaced`() {
        val client = mockClient { respond("", HttpStatusCode.ServiceUnavailable) }

        assertFailsWith<NewsFeedUnavailableException> {
            RssNewsSource(client, listOf("https://feed.example/down"))
                .fetch(DayWindow(LocalDate.of(2026, 11, 3)))
        }
    }

    @Test
    fun `composite tolerates one failed source but fails when every source fails`() {
        val article = Article("a", "headline", "body", "outlet", "https://example.com/a", LocalDate.of(2026, 11, 3))
        val working = object : com.marketbrief.domain.NewsSource {
            override fun fetch(window: DayWindow) = listOf(article)
        }
        val failing = object : com.marketbrief.domain.NewsSource {
            override fun fetch(window: DayWindow): List<Article> = throw NewsFeedUnavailableException("down")
        }

        assertEquals(
            listOf(article),
            CompositeNewsSource(listOf(failing, working)).fetch(DayWindow(article.publishedAt)),
        )
        assertFailsWith<NewsFeedUnavailableException> {
            CompositeNewsSource(listOf(failing)).fetch(DayWindow(article.publishedAt))
        }
    }

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/fixtures/news/$name")).readText()

    private fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine) {
            engine { addHandler { handler(it) } }
        }
}
