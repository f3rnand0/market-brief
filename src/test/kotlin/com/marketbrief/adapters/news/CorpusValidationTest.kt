package com.marketbrief.adapters.news

import com.marketbrief.domain.Article
import com.marketbrief.domain.NewsItem
import com.marketbrief.domain.NewsItemType
import com.marketbrief.domain.Region
import com.marketbrief.domain.ReportAssembler
import com.marketbrief.domain.Snapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorpusValidationTest {
    @Test
    fun `Gold set covers 32 full-text Articles and 21 type-region combinations`() {
        val articles = loadArticles()
        val goldSet = JsonGoldSet.fromResource()
        val items = goldSet.cases.flatMap { it.expectedItems }
        val combinations = items.map { it.type to it.region }.toSet()

        assertEquals(32, articles.size)
        assertEquals(32, goldSet.cases.size)
        assertEquals(32, items.size)
        assertTrue(combinations.size >= 21)
        assertTrue(NewsItemType.entries.all { type -> combinations.any { it.first == type } })
        assertTrue(Region.entries.all { region -> combinations.any { it.second == region } })
    }

    @Test
    fun `32-Article corpus produces stable regional selections`() {
        val articles = loadArticles()
        val goldItems = JsonGoldSet.fromResource().cases.flatMap { it.expectedItems }
        assertEquals(32, articles.size)
        assertEquals(32, goldItems.size)
        val report = ReportAssembler.assemble(
            LocalDate.of(2026, 11, 6),
            Snapshot(emptyList()),
            articles,
            goldItems,
        )
        val articleById = articles.associateBy(Article::id)

        assertEquals(
            mapOf(
                Region.US to listOf("article-025", "article-027", "article-001", "article-001"),
                Region.CHINA to listOf("article-002", "article-002"),
                Region.EUROPE to listOf("article-010", "article-016", "article-003", "article-003"),
                Region.LATAM to listOf("article-004", "article-004"),
                Region.ASIA to listOf("article-012", "article-005", "article-005"),
            ),
            report.regionalNews.mapValues { (_, items) -> items.map { it.source.articleId } },
        )
        report.regionalNews.forEach { (_, items) ->
            assertTrue(items.size <= 5)
            assertTrue(
                items.groupingBy { articleById.getValue(it.source.articleId).outlet }
                    .eachCount().values.all { it <= 2 },
            )
            assertEquals(items.map { it.eventKey() }.size, items.map { it.eventKey() }.toSet().size)
        }
        assertEquals(3, report.otherNews.size)
        assertTrue(report.companySectorNews.isNotEmpty())
        assertTrue(report.regionalNews.values.flatten().none { it.region == Region.GLOBAL })
        assertEquals(21, goldItems.map { it.type to it.region }.toSet().size)
        assertTrue(
            NewsItemType.entries.all { type ->
                report.regionalNews.values.flatten().any { it.type == type } ||
                    type in setOf(NewsItemType.COMPANY, NewsItemType.SECTOR)
            },
        )
    }

    @Test
    fun `corpus ids are unique and every gold case has an article`() {
        val articles = loadArticles()
        val ids = articles.map(Article::id)
        assertEquals(ids.size, ids.toSet().size, "corpus article ids must be unique")
        assertTrue(articles.all { it.body != it.headline }, "corpus Articles must have text beyond the headline")
        assertTrue(articles.none { it.body.contains("<a href=", ignoreCase = true) })

        val goldIds = JsonGoldSet.fromResource().cases.map { it.articleId }
        assertTrue(ids.toSet().containsAll(goldIds), "every gold case must have a corpus article")
        assertTrue(goldIds.toSet().containsAll(ids), "every corpus article must have a gold case")
    }

    @Test
    fun `gold citations occur in their source article`() {
        val articles = loadArticles().associateBy(Article::id)
        JsonGoldSet.fromResource().cases.forEach { goldCase ->
            val article = articles[goldCase.articleId]
                ?: error("Gold case has no corpus article: ${goldCase.articleId}")
            goldCase.expectedItems.flatMap { it.source.quotes }.forEach { quote ->
                assertTrue(
                    article.body.contains(quote),
                    "Citation for ${goldCase.articleId} is not present in article body: $quote",
                )
            }
        }
    }

    private fun loadArticles(): List<Article> {
        val resource = javaClass.classLoader.getResourceAsStream("corpus/articles.json")
            ?: error("Corpus resource not found")
        return resource.bufferedReader().use { reader ->
            Json.decodeFromString<List<CorpusArticle>>(reader.readText()).map { article ->
                Article(
                    id = article.id,
                    headline = article.headline,
                    body = article.body,
                    outlet = article.outlet,
                    url = article.url,
                    publishedAt = LocalDate.parse(article.publishedAt),
                )
            }
        }
    }

    private fun NewsItem.eventKey(): String = listOf(
        type.name,
        region.name,
        subjects.map { it.name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") }
            .distinct()
            .sorted()
            .joinToString("|"),
    ).joinToString(":")
}

@Serializable
private data class CorpusArticle(
    val id: String,
    val headline: String,
    val body: String,
    val outlet: String,
    val url: String,
    val publishedAt: String,
)
