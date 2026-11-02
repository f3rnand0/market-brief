package com.marketbrief.adapters.news

import com.marketbrief.domain.Article
import com.marketbrief.domain.DayWindow
import com.marketbrief.domain.NewsSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

class RssNewsSource(
    private val client: HttpClient,
    private val feedUrls: List<String> = DEFAULT_FEEDS,
) : NewsSource {
    override fun fetch(window: DayWindow): List<Article> = runBlocking {
        feedUrls.flatMap { url ->
            runCatching { RssParser.parse(client.get(url).bodyAsText()) }.getOrElse { emptyList() }
        }.filter { it.publishedAt == window.date }.distinctBy { it.url }
    }

    companion object {
        val DEFAULT_FEEDS = listOf(
            "https://news.google.com/rss/search?q=markets+finance&hl=en-US&gl=US&ceid=US:en",
            "https://feeds.reuters.com/reuters/businessNews",
            "https://feeds.bbci.co.uk/news/business/rss.xml",
        )
    }
}

class GdeltNewsSource(
    private val client: HttpClient,
    private val endpoint: String = "https://api.gdeltproject.org/api/v2/doc/doc?query=markets&mode=artlist&format=rss&maxrecords=250",
) : NewsSource {
    override fun fetch(window: DayWindow): List<Article> = runBlocking {
        runCatching {
            RssParser.parse(client.get(endpoint).bodyAsText())
                .filter { it.publishedAt == window.date }.distinctBy { it.url }
        }.getOrElse { emptyList() }
    }
}

class CompositeNewsSource(private val sources: List<NewsSource>) : NewsSource {
    override fun fetch(window: DayWindow): List<Article> = sources.flatMap { it.fetch(window) }
        .distinctBy { it.url.ifBlank { it.id } }
        .sortedWith(compareByDescending<Article> { it.publishedAt }.thenBy { it.id })
}

private object RssParser {
    private val dateFormats = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ISO_DATE_TIME,
        DateTimeFormatter.ISO_INSTANT,
    )

    fun parse(xml: String): List<Article> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
        return document.getElementsByTagName("item").let { nodes ->
            (0 until nodes.length).mapNotNull { index ->
                val node = nodes.item(index)
                fun field(name: String) = node.childNodes.let { children ->
                    (0 until children.length).firstNotNullOfOrNull { i ->
                        children.item(i).takeIf { it.nodeName.equals(name, true) }?.textContent?.trim()
                    }.orEmpty()
                }
                val url = field("link").ifBlank { field("guid") }
                val headline = field("title")
                val date = parseDate(field("pubDate").ifBlank { field("published") }) ?: return@mapNotNull null
                if (url.isBlank() || headline.isBlank()) return@mapNotNull null
                Article(
                    id = "rss-" + url.hashCode().toUInt().toString(16),
                    headline = headline,
                    body = field("description").ifBlank { headline },
                    outlet = runCatching { URI(url).host.removePrefix("www.") }.getOrDefault("RSS"),
                    url = url,
                    publishedAt = date,
                )
            }
        }
    }

    private fun parseDate(value: String): LocalDate? = dateFormats.firstNotNullOfOrNull { formatter ->
        runCatching {
            when (formatter) {
                DateTimeFormatter.ISO_INSTANT -> Instant.parse(value).atZone(ZoneOffset.UTC).toLocalDate()
                else -> java.time.ZonedDateTime.parse(value, formatter).toLocalDate()
            }
        }.getOrNull()
    }
}
