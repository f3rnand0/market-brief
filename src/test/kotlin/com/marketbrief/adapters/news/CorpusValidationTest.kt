package com.marketbrief.adapters.news

import com.marketbrief.domain.Article
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CorpusValidationTest {
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
