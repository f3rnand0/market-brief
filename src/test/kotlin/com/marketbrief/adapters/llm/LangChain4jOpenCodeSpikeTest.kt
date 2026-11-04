package com.marketbrief.adapters.llm

import com.marketbrief.domain.Article
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Tag
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

@Tag("live")
class LangChain4jOpenCodeSpikeTest {
    @Test
    fun `probes selected OpenCode Go models against the corpus`() = runBlocking {
        val token = System.getenv("OPENCODE_API_KEY") ?: error("OPENCODE_API_KEY is required")
        val articleLimit = System.getenv("SPIKE_ARTICLE_LIMIT")?.toIntOrNull() ?: Int.MAX_VALUE
        val corpus = loadArticles()
        val articlesToProbe = if (System.getenv("SPIKE_UNLABELED_ONLY") == "true") {
            val labeledIds = loadGoldIds().toSet()
            corpus.filterNot { it.id in labeledIds }
        } else {
            corpus
        }
        val articles = articlesToProbe.take(articleLimit)
        val models = System.getenv("SPIKE_MODELS")?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
            ?: MODELS
        val failures = mutableListOf<String>()
        val output = buildString {
            appendLine("# OpenCode structured-output probe")
            appendLine()
            models.forEach { model ->
                appendLine("## $model")
                articles.forEach { article ->
                    appendLine("### ${article.id}")
                    runCatching { request(token, model, article) }
                        .onSuccess { response ->
                            appendLine("```json")
                            appendLine(response)
                            appendLine("```")
                        }
                        .onFailure { error ->
                            failures += "$model / ${article.id}: ${error.message}"
                            appendLine("ERROR: ${error.message}")
                        }
                }
            }
        }
        val report = System.getenv("SPIKE_REPORT") ?: "build/reports/langchain4j-opencode.md"
        java.io.File(report).apply {
            parentFile?.mkdirs()
            writeText(output)
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    private fun request(token: String, model: String, article: Article): String {
        val responsesApi = model.startsWith("gpt-")
        val endpoint = if (responsesApi) "/responses" else "/chat/completions"
        val request = HttpRequest.newBuilder(URI.create(BASE_URL + endpoint))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("User-Agent", "market-brief/0.1.0")
            .header("x-opencode-session", SESSION_ID)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    if (responsesApi) responseBody(model, article) else requestBody(model, article),
                ),
            )
            .build()
        val response = HTTP.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "OpenCode request failed for $model (${response.statusCode()}): ${response.body()}"
        }
        return response.body()
    }

    private fun requestBody(model: String, article: Article): String = Json.encodeToString(
        ChatRequest(
            model = model,
            messages = listOf(
                Message("system", PROMPT),
                Message("user", "Headline: ${article.headline}\nBody:\n${article.body}"),
            ),
            responseFormat = ResponseFormat("json_object"),
        ),
    )

    private fun responseBody(model: String, article: Article): String = Json.encodeToString(
        ResponsesRequest(
            model = model,
            input = "Headline: ${article.headline}\nBody:\n${article.body}\n\n$PROMPT",
        ),
    )

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
        private val PROMPT = """
            Extract market news from the article. Return only JSON in this shape:
            {"items":[{"type":"MARKET_MOVE|COMPANY|SECTOR|MACRO","region":"US|CHINA|EUROPE|LATAM|ASIA|GLOBAL","subjects":["..."],"assertion":"one sentence","direction":"UP|DOWN|FLAT or null","cause":"string or null","metrics":[{"value":"...","unit":"..."}],"source":{"quotes":["verbatim article quote"]}}]}
            Use only the listed enum values. Every source quote must be copied exactly from the article.
            Use an empty items array when there are no relevant claims.
        """.trimIndent()
        private val MODELS = listOf("deepseek-v4.1-flash", "glm-5.3-flash", "gpt-6-luna")
        private val HTTP = HttpClient.newHttpClient()
        private val SESSION_ID = UUID.randomUUID().toString()
    }
}

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    @kotlinx.serialization.SerialName("response_format") val responseFormat: ResponseFormat,
)

@Serializable
private data class Message(val role: String, val content: String)

@Serializable
private data class ResponseFormat(val type: String)

@Serializable
private data class ResponsesRequest(val model: String, val input: String)

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
