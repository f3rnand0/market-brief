package com.marketbrief.adapters.llm

import com.marketbrief.domain.Article
import com.marketbrief.domain.Direction
import com.marketbrief.domain.NewsItemType
import com.marketbrief.domain.Region
import com.sun.net.httpserver.HttpServer
import dev.langchain4j.exception.AuthenticationException
import dev.langchain4j.exception.RateLimitException
import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LangChain4jNewsItemExtractorTest {

    private val article = Article(
        id = "a1",
        headline = "Stocks slide as inflation runs hot",
        body = "The S&P 500 fell 1.2% on Tuesday after inflation data surprised markets.",
        outlet = "Reuters",
        url = "https://example.com/a1",
        publishedAt = LocalDate.of(2026, 10, 25),
    )

    @Test
    fun `maps cited candidates and injects the article id into the citation`() = runBlocking {
        val content = """
            {"items": [
              {
                "type": "MARKET_MOVE",
                "region": "US",
                "subjects": ["S&P 500"],
                "assertion": "The S&P 500 fell 1.2%.",
                "direction": "DOWN",
                "cause": "inflation data surprised markets",
                "metrics": [{"value": "-1.2", "unit": "%"}],
                "source": {"quotes": ["The S&P 500 fell 1.2% on Tuesday"]}
              },
              {
                "type": "COMPANY",
                "region": "US",
                "subjects": ["Apple"],
                "assertion": "Apple declined.",
                "source": null
              }
            ]}
        """.trimIndent()

        StubChatServer(content).use { server ->
            val extractor = extractor(server)

            val items = extractor.extract(article)

            assertEquals(1, items.size, "uncited candidate must not cross the port")
            val item = items.single()
            assertEquals(NewsItemType.MARKET_MOVE, item.type)
            assertEquals(Region.US, item.region)
            assertEquals(listOf("S&P 500"), item.subjects.map { it.name })
            assertEquals(Direction.DOWN, item.direction)
            assertEquals("inflation data surprised markets", item.cause)
            assertEquals(listOf("-1.2" to "%"), item.metrics.map { it.value to it.unit })
            assertEquals("a1", item.source.articleId)
            assertEquals(listOf("The S&P 500 fell 1.2% on Tuesday"), item.source.quotes)
            assertEquals(1, extractor.droppedCandidateCount)
        }
    }

    @Test
    fun `drops candidates that violate domain invariants and counts them`() = runBlocking {
        val content = """
            {"items": [
              {
                "type": "COMPANY",
                "region": "EUROPE",
                "subjects": ["ASML"],
                "assertion": "ASML rose.",
                "cause": "a cause is only allowed on MARKET_MOVE",
                "source": {"quotes": ["ASML rose."]}
              },
              {
                "type": "MACRO",
                "region": "MARS",
                "subjects": ["Rates"],
                "assertion": "Rates moved.",
                "source": {"quotes": ["Rates moved."]}
              },
              {
                "type": "SECTOR",
                "region": "ASIA",
                "subjects": ["Semiconductors"],
                "assertion": "Chip stocks gained.",
                "source": {"quotes": ["Chip stocks gained."]}
              }
            ]}
        """.trimIndent()

        StubChatServer(content).use { server ->
            val extractor = extractor(server)

            val items = extractor.extract(article)

            assertEquals(listOf("Semiconductors"), items.flatMap { item -> item.subjects }.map { it.name })
            assertEquals(2, extractor.droppedCandidateCount)
        }
    }

    @Test
    fun `requests a json-object completion and sends the article text`() = runBlocking {
        StubChatServer("""{"items": []}""").use { server ->
            extractor(server).extract(article)

            val request = Json.parseToJsonElement(server.requests.single()).jsonObject
            assertEquals(
                "json_object",
                request.getValue("response_format").jsonObject.getValue("type").jsonPrimitive.content,
            )
            assertEquals("stub-model", request.getValue("model").jsonPrimitive.content)

            val messages = request.getValue("messages").jsonArray.map { it.jsonObject }
            val system = messages.first { it.getValue("role").jsonPrimitive.content == "system" }
            assertTrue(system.getValue("content").jsonPrimitive.content.contains("source"))
            val user = messages.first { it.getValue("role").jsonPrimitive.content == "user" }
            val prompt = user.getValue("content").jsonPrimitive.content
            assertTrue(prompt.contains(article.headline))
            assertTrue(prompt.contains(article.body))
        }
    }

    @Test
    fun `malformed structured output yields no items`() = runBlocking {
        StubChatServer("not json at all").use { server ->
            val extractor = extractor(server)

            assertTrue(extractor.extract(article).isEmpty())
            assertEquals(0, extractor.droppedCandidateCount)
        }
    }

    @Test
    fun `authentication failures escape the per-article isolation`() {
        StubChatServer("""{"items": []}""", status = 401).use { server ->
            assertFailsWith<AuthenticationException> {
                runBlocking { extractor(server).extract(article) }
            }
        }
    }

    @Test
    fun `provider rate limiting propagates instead of yielding an empty result`() {
        StubChatServer("""{"error": "rate limited"}""", status = 429).use { server ->
            assertFailsWith<RateLimitException> {
                runBlocking { extractor(server).extract(article) }
            }
        }
    }

    @Test
    fun `fromConfig wires the provider settings into the chat model`() = runBlocking {
        StubChatServer("""{"items": []}""").use { server ->
            val extractor = LangChain4jNewsItemExtractor.fromConfig(
                OpenAiCompatibleConfig(
                    baseUrl = server.baseUrl,
                    model = "stub-model",
                    apiKey = "test-key",
                    requestTimeoutSeconds = 30,
                    maxRetries = 0,
                ),
            )

            extractor.extract(article)

            val request = Json.parseToJsonElement(server.requests.single()).jsonObject
            assertEquals("stub-model", request.getValue("model").jsonPrimitive.content)
        }
    }

    private fun extractor(server: StubChatServer) = LangChain4jNewsItemExtractor(
        OpenAiChatModel.builder()
            .baseUrl(server.baseUrl)
            .apiKey("test-key")
            .modelName("stub-model")
            .maxRetries(0)
            .build(),
    )
}

/**
 * Minimal OpenAI-compatible endpoint: returns [content] as the assistant message
 * of every completion and records the raw request bodies.
 */
private class StubChatServer(content: String, private val status: Int = 200) : AutoCloseable {

    val requests = CopyOnWriteArrayList<String>()

    private val executor = Executors.newSingleThreadExecutor()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { http ->
        http.createContext("/v1/chat/completions") { exchange ->
            requests += exchange.requestBody.use { it.readBytes() }.decodeToString()
            val body = completion(content).toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        http.executor = executor
        http.start()
    }

    val baseUrl: String
        get() = "http://127.0.0.1:${server.address.port}/v1"

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    private fun completion(content: String): String = buildJsonObject {
        put("id", "chatcmpl-stub")
        put("object", "chat.completion")
        put("created", 0)
        put("model", "stub-model")
        put(
            "choices",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("index", 0)
                        put(
                            "message",
                            buildJsonObject {
                                put("role", "assistant")
                                put("content", content)
                            },
                        )
                        put("finish_reason", "stop")
                    },
                )
            },
        )
        put(
            "usage",
            buildJsonObject {
                put("prompt_tokens", 1)
                put("completion_tokens", 1)
                put("total_tokens", 2)
            },
        )
    }.toString()
}
