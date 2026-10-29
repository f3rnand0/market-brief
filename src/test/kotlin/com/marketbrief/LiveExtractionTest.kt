package com.marketbrief

import com.marketbrief.adapters.llm.LangChain4jNewsItemExtractor
import com.marketbrief.config.ApplicationConfigLoader
import com.marketbrief.domain.Article
import com.marketbrief.toOpenAiCompatibleConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Asserts citation structure only; exact wording is non-deterministic and is
 * scored by the eval harness instead.
 */
@Tag("live")
class LiveExtractionTest {

    private val article = Article(
        id = "live-1",
        headline = "Stocks slide as inflation runs hot",
        body = "The S&P 500 dropped 1.2% on Tuesday as hotter-than-expected US inflation data " +
            "pushed Treasury yields higher. Brent crude rose 0.8% after OPEC announced production cuts.",
        outlet = "Live Test",
        url = "https://example.com/live-1",
        publishedAt = LocalDate.now(),
    )

    @Test
    fun `extracts cited items from the configured provider`() = runBlocking {
        val config = ApplicationConfigLoader.load().toOpenAiCompatibleConfig()
        val extractor = LangChain4jNewsItemExtractor.fromConfig(config)

        val items = extractor.extract(article)

        println("live extraction: ${items.size} item(s), ${extractor.droppedCandidateCount} dropped")
        assertTrue(items.isNotEmpty(), "expected at least one item from the sample article")
        val articleText = "${article.headline}\n${article.body}".normalized()
        items.forEach { item ->
            assertEquals(article.id, item.source.articleId)
            assertTrue(item.source.quotes.isNotEmpty())
            item.source.quotes.forEach { quote ->
                assertTrue(articleText.contains(quote.normalized()), "quote is not verbatim: $quote")
            }
        }
    }

    private fun String.normalized(): String = trim().replace(Regex("\\s+"), " ")
}
