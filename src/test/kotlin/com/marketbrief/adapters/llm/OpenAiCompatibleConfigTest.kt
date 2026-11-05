package com.marketbrief.adapters.llm

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenAiCompatibleConfigTest {

    @Test
    fun `toString redacts the api key`() {
        val config = OpenAiCompatibleConfig(
            baseUrl = "https://api.example.com/v1",
            model = "some-model",
            apiKey = "super-secret-key",
            requestTimeoutSeconds = 30,
            maxRetries = 1,
        )

        val rendered = config.toString()

        assertFalse(rendered.contains("super-secret-key"), "api key must not appear in toString")
        assertTrue(rendered.contains("https://api.example.com/v1"))
        assertTrue(rendered.contains("some-model"))
    }

    @Test
    fun `requires both token prices when cost estimation is configured`() {
        assertFailsWith<IllegalArgumentException> {
            OpenAiCompatibleConfig(
                baseUrl = "https://api.example.com/v1",
                model = "some-model",
                apiKey = "test-key",
                requestTimeoutSeconds = 30,
                maxRetries = 1,
                inputCostPerMillionUsd = 0.3,
            )
        }
    }
}
