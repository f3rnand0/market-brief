package com.marketbrief.adapters.marketdata

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class QuoteRequestPolicyTest {

    private val log = LoggerFactory.getLogger(QuoteRequestPolicyTest::class.java)

    @Test
    fun `successful responses return the body`() = runBlocking {
        val client = mockClient { respond("payload", HttpStatusCode.OK) }

        val body = QuoteRequestPolicy.bodyOrNull("Test", "target", log) { client.get(REQUEST_URL) }

        assertEquals("payload", body)
    }

    @Test
    fun `non-success responses return null`() = runBlocking {
        val client = mockClient { respond("", HttpStatusCode.InternalServerError) }

        val body = QuoteRequestPolicy.bodyOrNull("Test", "target", log) { client.get(REQUEST_URL) }

        assertNull(body)
    }

    @Test
    fun `a request exceeding its bound returns null`() = runBlocking {
        val client = mockClient {
            delay(1.seconds)
            respond("late", HttpStatusCode.OK)
        }

        val body = withTimeout(5.seconds) {
            QuoteRequestPolicy.bodyOrNull("Test", "target", log, 10.milliseconds) { client.get(REQUEST_URL) }
        }

        assertNull(body)
    }

    @Test
    fun `outer cancellation is not swallowed as a request timeout`() {
        val error = assertFailsWith<CancellationException> {
            runBlocking {
                val client = mockClient {
                    delay(1.seconds)
                    respond("late", HttpStatusCode.OK)
                }

                withTimeout(10.milliseconds) {
                    QuoteRequestPolicy.bodyOrNull("Test", "target", log, 5.seconds) { client.get(REQUEST_URL) }
                }
            }
        }

        assertTrue(error is TimeoutCancellationException)
    }

    private fun mockClient(handler: suspend MockRequestHandleScope.() -> HttpResponseData): HttpClient =
        HttpClient(MockEngine) {
            engine {
                addHandler { handler() }
            }
        }

    private companion object {
        const val REQUEST_URL = "https://example.com/quote"
    }
}
