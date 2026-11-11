package com.marketbrief.adapters.llm

import dev.langchain4j.model.openai.OpenAiChatModel
import java.net.URI
import java.time.Duration
import java.util.UUID

internal object OpenAiChatModelFactory {
    fun create(config: OpenAiCompatibleConfig): OpenAiChatModel = OpenAiChatModel.builder()
        .baseUrl(config.baseUrl)
        .apiKey(config.apiKey)
        .modelName(config.model)
        .timeout(Duration.ofSeconds(config.requestTimeoutSeconds))
        .maxRetries(config.maxRetries)
        .customHeaders(headersFor(config.baseUrl))
        .build()

    internal fun headersFor(baseUrl: String): Map<String, String> = if (isOpenCodeEndpoint(baseUrl)) {
        mapOf(
            "User-Agent" to "market-brief/0.1.0",
            "x-opencode-session" to UUID.randomUUID().toString(),
        )
    } else {
        emptyMap()
    }

    private fun isOpenCodeEndpoint(baseUrl: String): Boolean = runCatching { URI(baseUrl).host }
        .getOrNull()
        ?.let { it == "opencode.ai" || it.endsWith(".opencode.ai") }
        ?: false
}
