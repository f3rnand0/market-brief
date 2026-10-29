package com.marketbrief.adapters.llm

import com.marketbrief.domain.Article
import com.marketbrief.domain.Direction
import com.marketbrief.domain.Metric
import com.marketbrief.domain.NewsItem
import com.marketbrief.domain.NewsItemExtractor
import com.marketbrief.domain.NewsItemType
import com.marketbrief.domain.Region
import com.marketbrief.domain.Source
import com.marketbrief.domain.Subject
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.exception.LangChain4jException
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ResponseFormat
import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

class LangChain4jNewsItemExtractor(
    private val chatModel: ChatModel,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : NewsItemExtractor {

    private val droppedCandidates = AtomicLong()

    /** Candidates rejected by the mapping seam since this adapter was created. */
    val droppedCandidateCount: Long
        get() = droppedCandidates.get()

    override suspend fun extract(article: Article): List<NewsItem> {
        val content = try {
            chatModel.chatAsync(request(article)).await().aiMessage().text()
        } catch (e: CancellationException) {
            throw e
        } catch (e: LangChain4jException) {
            throw e
        }
        if (content.isNullOrBlank()) {
            log.warn("Extraction returned no content for article {}", article.id)
            return emptyList()
        }

        val response = try {
            json.decodeFromString<ExtractionResponse>(content)
        } catch (e: Exception) {
            log.warn("Extraction returned malformed JSON for article {}", article.id, e)
            return emptyList()
        }

        val items = response.items.mapNotNull { candidate -> toNewsItem(article, candidate) }
        val dropped = response.items.size - items.size
        if (dropped > 0) {
            log.warn("Dropped {} of {} candidates for article {}", dropped, response.items.size, article.id)
        }
        return items
    }

    private fun request(article: Article): ChatRequest = ChatRequest.builder()
        .messages(
            SystemMessage.from(SYSTEM_PROMPT),
            UserMessage.from(userPrompt(article)),
        )
        .responseFormat(ResponseFormat.JSON)
        .build()

    private fun toNewsItem(article: Article, candidate: CandidateItem): NewsItem? = try {
        NewsItem(
            type = enumValueOf<NewsItemType>(candidate.type.orEmpty().trim().uppercase()),
            region = enumValueOf<Region>(candidate.region.orEmpty().trim().uppercase()),
            subjects = candidate.subjects.map { Subject(it.trim()) },
            assertion = candidate.assertion.trim(),
            direction = candidate.direction?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { enumValueOf<Direction>(it.uppercase()) },
            cause = candidate.cause?.trim()?.takeIf { it.isNotEmpty() },
            metrics = candidate.metrics.map { Metric(it.value.trim(), it.unit.trim()) },
            source = Source(
                articleId = article.id,
                quotes = candidate.source?.quotes.orEmpty().map { it.trim() }.filter { it.isNotEmpty() },
            ),
        )
    } catch (e: IllegalArgumentException) {
        droppedCandidates.incrementAndGet()
        log.debug("Dropped candidate for article {}: {}", article.id, e.message)
        null
    }

    private fun userPrompt(article: Article): String = buildString {
        appendLine("Outlet: ${article.outlet}")
        appendLine("Headline: ${article.headline}")
        appendLine("Body:")
        append(article.body)
    }

    companion object {
        fun fromConfig(config: OpenAiCompatibleConfig): LangChain4jNewsItemExtractor {
            val chatModel = OpenAiChatModel.builder()
                .baseUrl(config.baseUrl)
                .apiKey(config.apiKey)
                .modelName(config.model)
                .timeout(Duration.ofSeconds(config.requestTimeoutSeconds))
                .maxRetries(config.maxRetries)
                .build()
            return LangChain4jNewsItemExtractor(chatModel)
        }

        private val log = LoggerFactory.getLogger(LangChain4jNewsItemExtractor::class.java)

        private val SYSTEM_PROMPT = """
            You extract typed market-news items from a single news article.

            Return ONLY a JSON object with this shape:
            {"items": [{
              "type": ${quotedNames(NewsItemType.entries)},
              "region": ${quotedNames(Region.entries)},
              "subjects": ["company, ticker, sector, index, or asset class", ...],
              "assertion": "one sentence stating the claim",
              "direction": ${quotedNames(Direction.entries)} | null,
              "cause": "reason for the move" | null,
              "metrics": [{"value": "-1.2", "unit": "%"}],
              "source": {"quotes": ["verbatim span copied from the article"]}
            }]}

            Rules:
            - Extract only claims the article itself states. Do not use outside knowledge or infer.
            - Every item needs at least one source quote copied verbatim from the article. If you cannot
              quote it, do not emit the item.
            - Set "direction" only for moves, and "cause" only for MARKET_MOVE items.
            - "subjects" must name what the item is about; never leave it empty.
            - "metrics" holds numbers stated in the article, each with its unit; use [] when there are none.
            - Return {"items": []} when the article carries no relevant market information.
        """.trimIndent()

        private fun quotedNames(values: Collection<Enum<*>>): String = values.joinToString(" | ") { "\"${it.name}\"" }
    }
}

@Serializable
private data class ExtractionResponse(val items: List<CandidateItem> = emptyList())

@Serializable
private data class CandidateItem(
    val type: String? = null,
    val region: String? = null,
    val subjects: List<String> = emptyList(),
    val assertion: String = "",
    val direction: String? = null,
    val cause: String? = null,
    val metrics: List<CandidateMetric> = emptyList(),
    val source: CandidateSource? = null,
)

@Serializable
private data class CandidateMetric(val value: String = "", val unit: String = "")

@Serializable
private data class CandidateSource(val quotes: List<String> = emptyList())
