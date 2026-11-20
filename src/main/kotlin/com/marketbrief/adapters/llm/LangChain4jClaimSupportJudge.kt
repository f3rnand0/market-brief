package com.marketbrief.adapters.llm

import com.marketbrief.domain.ClaimSupport
import com.marketbrief.domain.ClaimSupportContext
import com.marketbrief.domain.ClaimSupportJudge
import com.marketbrief.domain.ClaimSupportJudgment
import com.marketbrief.domain.NewsItem
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.exception.LangChain4jException
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.ResponseFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class LangChain4jClaimSupportJudge(
    private val chatModel: ChatModel,
    private val inputCostPerMillionUsd: Double? = null,
    private val outputCostPerMillionUsd: Double? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ClaimSupportJudge {
    override suspend fun judge(item: NewsItem, context: ClaimSupportContext): ClaimSupportJudgment {
        val response = try {
            chatModel.chatAsync(request(item, context)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: LangChain4jException) {
            throw e
        }
        val usage = response.tokenUsage()?.toExtractionUsage(inputCostPerMillionUsd, outputCostPerMillionUsd)
        val content = response.aiMessage().text()
        if (content.isNullOrBlank()) return ClaimSupportJudgment(ClaimSupport.INDETERMINATE, usage)

        val verdict = try {
            json.decodeFromString<JudgeResponse>(content).verdict
                ?.trim()
                ?.uppercase()
                ?.let { ClaimSupport.valueOf(it) }
                ?: ClaimSupport.INDETERMINATE
        } catch (e: Exception) {
            ClaimSupport.INDETERMINATE
        }
        return ClaimSupportJudgment(verdict, usage)
    }

    private fun request(item: NewsItem, context: ClaimSupportContext): ChatRequest = ChatRequest.builder()
        .messages(
            SystemMessage.from(SYSTEM_PROMPT),
            UserMessage.from(
                buildString {
                    appendLine("Assertion: ${item.assertion}")
                    appendLine("Article headline: ${context.headline}")
                    appendLine("Relevant Article text:")
                    appendLine(context.articleText)
                    appendLine("Cited quote spans:")
                    item.source.quotes.forEach { appendLine("- $it") }
                },
            ),
        )
        .responseFormat(ResponseFormat.JSON)
        .build()

    companion object {
        fun fromConfig(config: OpenAiCompatibleConfig): LangChain4jClaimSupportJudge {
            val chatModel = OpenAiChatModelFactory.create(config)
            return LangChain4jClaimSupportJudge(
                chatModel = chatModel,
                inputCostPerMillionUsd = config.inputCostPerMillionUsd,
                outputCostPerMillionUsd = config.outputCostPerMillionUsd,
            )
        }

        private val SYSTEM_PROMPT = """
            Decide whether the cited quote spans support the assertion.
            Treat each cited quote as the evidence under review.
            Use the cited quote spans and surrounding Article text to decide whether the assertion is supported.
            The surrounding text is context only; do not infer facts that the Article does not state.
            Return only a JSON object: {"verdict":"SUPPORTED|UNSUPPORTED|INDETERMINATE"}.
            Use SUPPORTED only when the quotes support the full assertion. Use UNSUPPORTED when they contradict it.
            Use INDETERMINATE when the quotes are insufficient, ambiguous, or only partly support it.
        """.trimIndent()
    }
}

@Serializable
private data class JudgeResponse(val verdict: String? = null)
