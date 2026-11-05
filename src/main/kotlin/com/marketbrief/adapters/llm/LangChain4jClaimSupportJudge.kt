package com.marketbrief.adapters.llm

import com.marketbrief.domain.ClaimSupport
import com.marketbrief.domain.ClaimSupportJudge
import com.marketbrief.domain.ClaimSupportJudgment
import com.marketbrief.domain.NewsItem
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
import java.time.Duration

class LangChain4jClaimSupportJudge(
    private val chatModel: ChatModel,
    private val inputCostPerMillionUsd: Double? = null,
    private val outputCostPerMillionUsd: Double? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ClaimSupportJudge {
    override suspend fun judge(item: NewsItem): ClaimSupportJudgment {
        val response = try {
            chatModel.chatAsync(request(item)).await()
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

    private fun request(item: NewsItem): ChatRequest = ChatRequest.builder()
        .messages(
            SystemMessage.from(SYSTEM_PROMPT),
            UserMessage.from(
                buildString {
                    appendLine("Assertion: ${item.assertion}")
                    appendLine("Cited quote spans:")
                    item.source.quotes.forEach { appendLine("- $it") }
                },
            ),
        )
        .responseFormat(ResponseFormat.JSON)
        .build()

    companion object {
        fun fromConfig(config: OpenAiCompatibleConfig): LangChain4jClaimSupportJudge {
            val chatModel = OpenAiChatModel.builder()
                .baseUrl(config.baseUrl)
                .apiKey(config.apiKey)
                .modelName(config.model)
                .timeout(Duration.ofSeconds(config.requestTimeoutSeconds))
                .maxRetries(config.maxRetries)
                .build()
            return LangChain4jClaimSupportJudge(
                chatModel = chatModel,
                inputCostPerMillionUsd = config.inputCostPerMillionUsd,
                outputCostPerMillionUsd = config.outputCostPerMillionUsd,
            )
        }

        private val SYSTEM_PROMPT = """
            Decide whether the cited quote spans support the assertion.
            Use only the quote spans. Do not use outside knowledge or assume facts not stated.
            Return only a JSON object: {"verdict":"SUPPORTED|UNSUPPORTED|INDETERMINATE"}.
            Use SUPPORTED only when the quotes support the full assertion. Use UNSUPPORTED when they contradict it.
            Use INDETERMINATE when the quotes are insufficient, ambiguous, or only partly support it.
        """.trimIndent()
    }
}

@Serializable
private data class JudgeResponse(val verdict: String? = null)
