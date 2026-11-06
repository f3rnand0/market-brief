package com.marketbrief.adapters.llm

import com.marketbrief.domain.Article
import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Tag
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

@Tag("live")
class LangChain4jOpenCodeSpikeTest {
    @Test
    fun `probes production LangChain4j extractor against OpenCode Go models`() = runBlocking {
        val token = System.getenv("OPENCODE_API_KEY") ?: error("OPENCODE_API_KEY is required")
        val articleLimit = System.getenv("SPIKE_ARTICLE_LIMIT")?.toIntOrNull() ?: Int.MAX_VALUE
        require(articleLimit > 0) { "SPIKE_ARTICLE_LIMIT must be positive" }
        val articles = loadArticles()
            .let { corpus ->
                if (System.getenv("SPIKE_UNLABELED_ONLY") == "true") {
                    val labeledIds = loadGoldIds().toSet()
                    corpus.filterNot { it.id in labeledIds }
                } else {
                    corpus
                }
            }
            .take(articleLimit)
        val models = System.getenv("SPIKE_MODELS")?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
            ?: MODELS
        require(models.isNotEmpty()) { "SPIKE_MODELS must select at least one model" }
        val results = models.map { model -> probe(token, model, articles) }
        val report = System.getenv("SPIKE_REPORT") ?: "build/reports/langchain4j-opencode.md"
        java.io.File(report).apply {
            parentFile?.mkdirs()
            writeText(renderReport(articles, results))
        }
        val providerFailures = results.flatMap { result ->
            result.failures.map { failure -> "${result.model} / ${failure.articleId}: ${failure.errorType}" }
        }
        assertTrue(providerFailures.isEmpty(), providerFailures.joinToString("\n"))
    }

    private suspend fun probe(token: String, model: String, articles: List<Article>): ModelResult {
        val chatModel = OpenAiChatModel.builder()
            .baseUrl(BASE_URL)
            .apiKey(token)
            .modelName(model)
            .timeout(Duration.ofSeconds(120))
            .maxRetries(2)
            .customHeaders(
                mapOf(
                    "User-Agent" to USER_AGENT,
                    "x-opencode-session" to UUID.randomUUID().toString(),
                ),
            )
            .build()
        val extractor = LangChain4jNewsItemExtractor(chatModel)
        val observations = mutableListOf<ArticleObservation>()
        val failures = mutableListOf<ProviderFailure>()
        for (article in articles) {
            val droppedBefore = extractor.droppedCandidateCount
            try {
                val extraction = extractor.extract(article)
                observations += ArticleObservation(
                    articleId = article.id,
                    itemCount = extraction.items.size,
                    missingDirection = extraction.items.count { it.direction == null },
                    missingCause = extraction.items.count { it.cause == null },
                    emptyMetrics = extraction.items.count { it.metrics.isEmpty() },
                    citationCount = extraction.items.sumOf { it.source.quotes.size },
                    unsupportedQuotes = extraction.items.sumOf { item ->
                        item.source.quotes.count { quote -> quote !in article.body }
                    },
                    droppedCandidates = extractor.droppedCandidateCount - droppedBefore,
                    usageAvailable = extraction.usage != null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += ProviderFailure(article.id, e::class.simpleName ?: "Exception")
            }
        }
        return ModelResult(model, observations, failures, extractor.droppedCandidateCount)
    }

    private fun renderReport(articles: List<Article>, results: List<ModelResult>): String = buildString {
        appendLine("# LangChain4j OpenCode structured-output probe")
        appendLine()
        appendLine("Probe path: `OpenAiChatModel` → production `LangChain4jNewsItemExtractor` → domain `NewsItem`.")
        appendLine("Raw provider responses and credentials are not written to this report.")
        appendLine("Articles attempted: ${articles.size}.")
        appendLine()
        appendLine("## Expected versus observed")
        appendLine()
        appendLine("| Contract | Expected | Observed |")
        appendLine("| --- | --- | --- |")
        appendLine(
            "| JSON envelope | `items` array parses into typed items | Valid returned items are counted below; " +
                "malformed JSON maps to zero items per the stub test |",
        )
        appendLine(
            "| Enums | Values belong to the domain enum sets | Returned values are typed enums; " +
                "invalid values are dropped and counted |",
        )
        appendLine(
            "| Optional fields | `direction`, `cause`, and metrics may be absent | " +
                "Absence counts are reported per model below |",
        )
        appendLine(
            "| Nested citation | Every item has a non-empty `source.quotes` list | " +
                "Quote counts and verbatim support checks are reported per model |",
        )
        appendLine(
            "| Provider errors | Authentication/rate limits propagate as errors | " +
                "Failed requests are counted by exception class; no response body is recorded |",
        )
        appendLine()
        appendLine("## Model results")
        appendLine()
        appendLine(
            "| Model | Requests | Provider errors | Items | Missing direction | Missing cause | Empty metrics | " +
                "Quotes | Unsupported quotes | Dropped candidates | Usage present |",
        )
        appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
        results.forEach { result ->
            val observations = result.observations
            appendLine(
                "| ${result.model} | ${observations.size + result.failures.size} | ${result.failures.size} | " +
                    "${observations.sumOf { it.itemCount }} | ${observations.sumOf { it.missingDirection }} | " +
                    "${observations.sumOf { it.missingCause }} | ${observations.sumOf { it.emptyMetrics }} | " +
                    "${observations.sumOf { it.citationCount }} | ${observations.sumOf { it.unsupportedQuotes }} | " +
                    "${result.droppedCandidates} | ${observations.count { it.usageAvailable }} |",
            )
        }
        appendLine()
        appendLine("## Per-article observations")
        appendLine()
        appendLine(
            "| Model | Article | Items | Missing direction | Missing cause | Empty metrics | Quotes | " +
                "Unsupported quotes | Dropped candidates | Usage |",
        )
        appendLine("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |")
        results.forEach { result ->
            result.observations.forEach { observation ->
                appendLine(
                    "| ${result.model} | ${observation.articleId} | ${observation.itemCount} | " +
                        "${observation.missingDirection} | ${observation.missingCause} | " +
                        "${observation.emptyMetrics} | " +
                        "${observation.citationCount} | ${observation.unsupportedQuotes} | " +
                        "${observation.droppedCandidates} | ${observation.usageAvailable} |",
                )
            }
            result.failures.forEach { failure ->
                appendLine("| ${result.model} | ${failure.articleId} | error: ${failure.errorType} | | | | | | | |")
            }
        }
        appendLine()
        appendLine(
            "Interpretation: item counts describe the adapter's typed output, not semantic correctness. " +
                "Gold-set matching remains the separate M7 evaluation.",
        )
    }

    private fun loadArticles(): List<Article> {
        val stream = javaClass.classLoader.getResourceAsStream("corpus/articles.json")
            ?: error("Corpus resource not found")
        return stream.bufferedReader().use { reader ->
            Json.decodeFromString<List<CorpusArticle>>(reader.readText()).map { it.toArticle() }
        }
    }

    private fun loadGoldIds(): List<String> {
        val stream = javaClass.classLoader.getResourceAsStream("gold/index.json")
            ?: error("Gold index not found")
        return stream.bufferedReader().use { Json.decodeFromString(it.readText()) }
    }

    companion object {
        private const val BASE_URL = "https://opencode.ai/inference/go/openai/v1"
        private const val USER_AGENT = "market-brief/0.1.0"
        private val MODELS = listOf("deepseek-v4.1-flash", "glm-5.3-flash")
    }
}

private data class ModelResult(
    val model: String,
    val observations: List<ArticleObservation>,
    val failures: List<ProviderFailure>,
    val droppedCandidates: Long,
)

private data class ArticleObservation(
    val articleId: String,
    val itemCount: Int,
    val missingDirection: Int,
    val missingCause: Int,
    val emptyMetrics: Int,
    val citationCount: Int,
    val unsupportedQuotes: Int,
    val droppedCandidates: Long,
    val usageAvailable: Boolean,
)

private data class ProviderFailure(val articleId: String, val errorType: String)

@Serializable
private data class CorpusArticle(
    val id: String,
    val headline: String,
    val body: String,
    val outlet: String,
    val url: String,
    val publishedAt: String,
) {
    fun toArticle() = Article(id, headline, body, outlet, url, LocalDate.parse(publishedAt))
}
