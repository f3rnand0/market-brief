package com.marketbrief.adapters.news

import com.marketbrief.domain.Article
import com.marketbrief.domain.DayWindow
import com.marketbrief.domain.NewsSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * The PoC's curated static corpus (PRD §8). Discovery becomes live in M8; until
 * then this keeps the pipeline deterministic and offline.
 */
class JsonCorpusNewsSource(private val articles: List<Article>) : NewsSource {

    override fun fetch(window: DayWindow): List<Article> = articles

    companion object {
        const val DEFAULT_RESOURCE = "/corpus/articles.json"

        private val json = Json { ignoreUnknownKeys = true }

        fun fromResource(
            resource: String = DEFAULT_RESOURCE,
            loader: ClassLoader = JsonCorpusNewsSource::class.java.classLoader,
        ): JsonCorpusNewsSource {
            val text = loader.getResourceAsStream(resource.removePrefix("/"))
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Corpus resource not found: $resource")
            return JsonCorpusNewsSource(
                json.decodeFromString<List<CorpusArticle>>(text).map { article ->
                    Article(
                        id = article.id,
                        headline = article.headline,
                        body = article.body,
                        outlet = article.outlet,
                        url = article.url,
                        publishedAt = LocalDate.parse(article.publishedAt),
                    )
                },
            )
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
