package com.marketbrief.adapters.news

import com.marketbrief.domain.DayWindow
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublisherArticleTextFetcherTest {
    @Test
    fun `fetches publisher article text before returning an Article`() {
        val articleText = List(12) { "The publisher reported a new market development with details for investors." }
            .joinToString(" ")
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                "/feed" -> respond(
                    rss("https://publisher.example/article"),
                    headers = headersOf(HttpHeaders.ContentType, "application/rss+xml"),
                )

                "/article" -> respond(
                    "<html><main><article>${paragraphs(articleText)}</article></main></html>",
                    headers = headersOf(HttpHeaders.ContentType, "text/html"),
                )

                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val articles = RssNewsSource(client, listOf("https://feed.example/feed"))
            .fetch(DayWindow(LocalDate.of(2026, 11, 3)))

        assertEquals(1, articles.size)
        assertEquals("Example Publisher", articles.single().outlet)
        assertTrue(articles.single().body.contains("new market development"))
        assertTrue(articles.single().body.split(Regex("\\s+")).size >= 80)
    }

    @Test
    fun `does not return a headline or short teaser when publisher text is unavailable`() {
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                "/feed" -> respond(rss("https://publisher.example/article"))

                "/article" -> respond(
                    "<html><article><h1>Headline only</h1><p>A short RSS-style teaser.</p></article></html>",
                    headers = headersOf(HttpHeaders.ContentType, "text/html"),
                )

                else -> respond("", HttpStatusCode.NotFound)
            }
        }

        val articles = RssNewsSource(client, listOf("https://feed.example/feed"))
            .fetch(DayWindow(LocalDate.of(2026, 11, 3)))

        assertTrue(articles.isEmpty())
    }

    private fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine) {
            engine {
                addHandler { handler(it) }
            }
        }

    private fun rss(articleUrl: String) = """
        <rss version="2.0"><channel><item>
          <title>Market headline</title>
          <link>$articleUrl</link>
          <pubDate>Tue, 03 Nov 2026 09:00:00 GMT</pubDate>
          <source url="https://publisher.example">Example Publisher</source>
          <description>Headline-only teaser.</description>
        </item></channel></rss>
    """.trimIndent()

    private fun paragraphs(text: String) = "<p>$text</p><p>$text</p><p>$text</p>"
}
