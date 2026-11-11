package com.marketbrief.adapters.news

import com.marketbrief.domain.Article
import com.marketbrief.domain.DayWindow
import com.marketbrief.domain.NewsSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CancellationException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class RssNewsSource(private val client: HttpClient, private val feedUrls: List<String> = DEFAULT_FEEDS) : NewsSource {

    private val articleTextFetcher = PublisherArticleTextFetcher(client)

    override fun fetch(window: DayWindow): List<Article> = runBlocking {
        var successfulFeeds = 0
        val candidates = feedUrls.flatMap { url ->
            try {
                val entries = RssParser.parse(client.get(url).successfulBody())
                successfulFeeds++
                entries
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Could not read news feed {}", url, e)
                emptyList()
            }
        }.also {
            if (successfulFeeds == 0 &&
                feedUrls.isNotEmpty()
            ) {
                throw NewsFeedUnavailableException("All RSS feeds failed")
            }
        }
            .filter { it.publishedAt == window.date }.distinctBy { it.url }
        candidates.mapNotNull { candidate ->
            try {
                articleTextFetcher.fetch(candidate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Could not fetch publisher article {}", candidate.url, e)
                null
            }
        }
    }

    companion object {
        val DEFAULT_FEEDS = listOf(
            "https://feeds.bbci.co.uk/news/business/rss.xml",
            "https://feeds.bbci.co.uk/news/world/asia/rss.xml",
            "https://www.theguardian.com/business/rss",
            "https://www.cnbc.com/id/100003114/device/rss/rss.html",
            "https://en.mercopress.com/rss/economy",
            "https://en.mercopress.com/rss/latin-america",
        )

        private val log = LoggerFactory.getLogger(RssNewsSource::class.java)
    }
}

class GdeltNewsSource(
    private val client: HttpClient,
    private val endpoint: String =
        "https://api.gdeltproject.org/api/v2/doc/doc?query=markets&mode=artlist&format=rss&maxrecords=250",
) : NewsSource {

    private val articleTextFetcher = PublisherArticleTextFetcher(client)

    override fun fetch(window: DayWindow): List<Article> = runBlocking {
        val candidates = try {
            RssParser.parse(fetchFeed())
                .filter { it.publishedAt == window.date }.distinctBy { it.url }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not read GDELT feed {}", endpoint, e)
            throw NewsFeedUnavailableException("GDELT feed failed: $endpoint")
        }
        candidates.mapNotNull { candidate ->
            try {
                articleTextFetcher.fetch(candidate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("Could not fetch publisher article {}", candidate.url, e)
                null
            }
        }
    }

    private suspend fun fetchFeed(): String {
        val response = client.get(endpoint)
        if (response.status.value == 429) {
            delay(1_000)
            val retry = client.get(endpoint)
            return retry.successfulBody()
        }
        return response.successfulBody()
    }

    companion object {
        private val log = LoggerFactory.getLogger(GdeltNewsSource::class.java)
    }
}

class CompositeNewsSource(private val sources: List<NewsSource>) : NewsSource {
    override fun fetch(window: DayWindow): List<Article> {
        var successfulSources = 0
        val articles = sources.flatMap { source ->
            try {
                source.fetch(window).also { successfulSources++ }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("News source failed; continuing with remaining sources", e)
                emptyList()
            }
        }
        if (successfulSources == 0 &&
            sources.isNotEmpty()
        ) {
            throw NewsFeedUnavailableException("All news sources failed")
        }
        return articles.distinctBy { it.url.ifBlank { it.id } }
            .sortedWith(compareByDescending<Article> { it.publishedAt }.thenBy { it.id })
    }

    private companion object {
        val log = LoggerFactory.getLogger(CompositeNewsSource::class.java)
    }
}

class NewsFeedUnavailableException(message: String) : RuntimeException(message)

private suspend fun HttpResponse.successfulBody(): String {
    check(status.value in 200..299) { "News feed returned HTTP ${status.value}" }
    return bodyAsText()
}

private object RssParser {
    private val dateFormats = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ISO_DATE_TIME,
        DateTimeFormatter.ISO_INSTANT,
    )

    fun parse(xml: String): List<RssEntry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
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
                val source = (0 until node.childNodes.length).firstNotNullOfOrNull { i ->
                    node.childNodes.item(i).takeIf { it.nodeName.equals("source", true) }
                }
                RssEntry(
                    id = "rss-" + url.hashCode().toUInt().toString(16),
                    headline = headline,
                    outlet = source?.textContent?.trim()?.takeIf { it.isNotBlank() }
                        ?: runCatching { URI(url).host.removePrefix("www.") }.getOrDefault("RSS"),
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

private data class RssEntry(
    val id: String,
    val headline: String,
    val outlet: String,
    val url: String,
    val publishedAt: LocalDate,
)

private class PublisherArticleTextFetcher(private val client: HttpClient) {
    private val redirectDisabledClient = client.config { followRedirects = false }

    suspend fun fetch(entry: RssEntry): Article? {
        val uri = URI(entry.url)
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        if (uri.host.equals("news.google.com", ignoreCase = true)) return null
        if (!uri.hasPublicAddress()) return null

        val response = withTimeout(ARTICLE_REQUEST_TIMEOUT_MILLIS) {
            redirectDisabledClient.get(entry.url) {
                headers.append("User-Agent", USER_AGENT)
                headers.append("Accept", "text/html,application/xhtml+xml")
            }
        }
        if (response.status.value !in 200..299) return null

        val document = Jsoup.parse(response.bodyAsText(), entry.url)
        document.select("script,style,noscript,nav,footer,aside,form,button").remove()
        val articleBody = ARTICLE_BODY_SELECTORS.firstNotNullOfOrNull { selector ->
            document.selectFirst(selector)?.let(::bodyText)?.takeIf(::hasArticleLength)
        } ?: return null

        return Article(
            id = entry.id,
            headline = entry.headline,
            body = articleBody,
            outlet = entry.outlet,
            url = document.selectFirst("link[rel=canonical]")?.absUrl("href")?.takeIf { it.isNotBlank() }
                ?: entry.url,
            publishedAt = entry.publishedAt,
        )
    }

    private fun bodyText(element: org.jsoup.nodes.Element): String {
        val paragraphs = element.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
        return paragraphs.ifEmpty { listOf(element.text()) }.joinToString("\n")
    }

    private fun hasArticleLength(text: String): Boolean = text.split(Regex("\\s+")).size >= MINIMUM_ARTICLE_WORDS

    private fun URI.hasPublicAddress(): Boolean = try {
        InetAddress.getAllByName(host).all { it.isPublicAddress() }
    } catch (_: UnknownHostException) {
        false
    }

    private fun InetAddress.isPublicAddress(): Boolean {
        val address = address
        if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress) {
            return false
        }
        if (address.size == 4) {
            val first = address[0].toInt() and 0xff
            val second = address[1].toInt() and 0xff
            return when {
                first == 0 || first == 10 || first == 127 || first >= 224 -> false
                first == 100 && second in 64..127 -> false
                first == 169 && second == 254 -> false
                first == 172 && second in 16..31 -> false
                first == 192 && second == 0 && (address[2].toInt() and 0xff) == 0 -> false
                first == 192 && second == 2 -> false
                first == 192 && second == 168 -> false
                first == 192 && second == 88 && (address[2].toInt() and 0xff) == 99 -> false
                first == 198 && second in setOf(18, 19, 51) -> false
                first == 203 && second == 0 && (address[2].toInt() and 0xff) == 113 -> false
                else -> true
            }
        }
        if (address.size == 16) {
            val first = address[0].toInt() and 0xff
            val second = address[1].toInt() and 0xff
            return when {
                first and 0xfe == 0xfc -> false
                first == 0xfe && second and 0xc0 == 0x80 -> false
                first == 0xff -> false
                first == 0x20 && second == 0x01 && address[2] == 0x0d.toByte() && address[3] == 0xb8.toByte() -> false
                else -> true
            }
        }
        return false
    }

    private companion object {
        const val ARTICLE_REQUEST_TIMEOUT_MILLIS = 15_000L
        const val MINIMUM_ARTICLE_WORDS = 80
        const val USER_AGENT = "market-brief/0.1.0"
        val ARTICLE_BODY_SELECTORS = listOf(
            "[itemprop=articleBody]",
            "[data-test=articleBody]",
            ".ArticleBody-articleBody",
            "section.content-page",
            "article",
            "main",
        )
    }
}
