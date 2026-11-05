package com.marketbrief.adapters.llm

import com.marketbrief.domain.ClaimSupport
import com.marketbrief.domain.item
import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LangChain4jClaimSupportJudgeTest {
    @Test
    fun `evaluates cited quote support and returns token usage`() = runBlocking {
        StubChatServer("""{"verdict":"UNSUPPORTED"}""").use { server ->
            val judge = judge(server)

            val result = judge.judge(item(assertion = "The company doubled its revenue."))

            assertEquals(ClaimSupport.UNSUPPORTED, result.support)
            assertEquals(1, result.usage?.inputTokens)
            assertEquals(1, result.usage?.outputTokens)
            assertEquals("0.000001500000", result.usage?.estimatedCostUsd.toString())
            val request = Json.parseToJsonElement(server.requests.single()).jsonObject
            assertEquals(
                "json_object",
                request.getValue("response_format").jsonObject.getValue("type").jsonPrimitive.content,
            )
            assertTrue(request.getValue("messages").jsonArray.toString().contains("supporting quote"))
            assertTrue(request.getValue("messages").jsonArray.toString().contains("doubled its revenue"))
        }
    }

    @Test
    fun `malformed judge output is indeterminate`() = runBlocking {
        StubChatServer("not json").use { server ->
            val result = judge(server).judge(item())

            assertEquals(ClaimSupport.INDETERMINATE, result.support)
            assertEquals(1, result.usage?.inputTokens)
        }
    }

    private fun judge(server: StubChatServer) = LangChain4jClaimSupportJudge(
        chatModel = OpenAiChatModel.builder()
            .baseUrl(server.baseUrl)
            .apiKey("test-key")
            .modelName("stub-model")
            .maxRetries(0)
            .build(),
        inputCostPerMillionUsd = 0.3,
        outputCostPerMillionUsd = 1.2,
    )
}
