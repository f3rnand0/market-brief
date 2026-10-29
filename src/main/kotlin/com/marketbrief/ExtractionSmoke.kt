package com.marketbrief

import com.marketbrief.adapters.llm.LangChain4jNewsItemExtractor
import com.marketbrief.config.ApplicationConfigLoader
import com.marketbrief.config.toOpenAiCompatibleConfig
import com.marketbrief.domain.Article
import java.time.LocalDate

/**
 * Manual smoke run for the LLM seam: reads runtime config, takes the API key
 * from the environment (1Password's `op run`), and extracts one sample article.
 */
fun main() {
    val config = ApplicationConfigLoader.load().toOpenAiCompatibleConfig()
    val extractor = LangChain4jNewsItemExtractor.fromConfig(config)

    val article = Article(
        id = "smoke-1",
        headline = "Stocks slide as inflation runs hot",
        body = "The S&P 500 dropped 1.2% on Tuesday as hotter-than-expected US inflation data " +
            "pushed Treasury yields higher. Brent crude rose 0.8% after OPEC announced production cuts.",
        outlet = "Smoke Test",
        url = "https://example.com/smoke-1",
        publishedAt = LocalDate.now(),
    )

    val items = extractor.extract(article)
    items.forEach { item ->
        println("[${item.type}/${item.region}] ${item.assertion} <- ${item.source.quotes}")
    }
    println("Extracted ${items.size} item(s); dropped ${extractor.droppedCandidateCount} candidate(s)")
}
