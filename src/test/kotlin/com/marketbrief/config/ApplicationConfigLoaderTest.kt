package com.marketbrief.config

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApplicationConfigLoaderTest {

    @Test
    fun `parses non-sensitive settings from yaml`() {
        val config = ApplicationConfigLoader.parse(
            """
            llm:
              baseUrl: https://api.deepseek.com/v1
              model: deepseek-chat
              apiKeyEnv: DEEPSEEK_KEY
              requestTimeoutSeconds: 90
              maxRetries: 3
            """.trimIndent(),
        )

        assertEquals("https://api.deepseek.com/v1", config.llm.baseUrl)
        assertEquals("deepseek-chat", config.llm.model)
        assertEquals("DEEPSEEK_KEY", config.llm.apiKeyEnv)
        assertEquals(90, config.llm.requestTimeoutSeconds)
        assertEquals(3, config.llm.maxRetries)
    }

    @Test
    fun `applies defaults and ignores unknown keys`() {
        val config = ApplicationConfigLoader.parse(
            """
            llm:
              baseUrl: https://api.example.com/v1
              model: some-model
              unannouncedSetting: true
            server:
              port: 9000
            """.trimIndent(),
        )

        assertEquals("LLM_API_KEY", config.llm.apiKeyEnv)
        assertEquals(120, config.llm.requestTimeoutSeconds)
        assertEquals(2, config.llm.maxRetries)
    }

    @Test
    fun `loads the repository config file`() {
        val config = ApplicationConfigLoader.load(Path.of(ApplicationConfigLoader.DEFAULT_PATH))

        assertTrue(config.llm.baseUrl.startsWith("http"))
        assertTrue(config.llm.model.isNotBlank())
    }

    @Test
    fun `blank config path falls back to the default`() {
        val config = ApplicationConfigLoader.load(mapOf(ApplicationConfigLoader.PATH_ENV to ""))

        assertTrue(config.llm.baseUrl.startsWith("http"))
    }

    @Test
    fun `reports an unreadable config path`() {
        val path = Path.of("config/does-not-exist.yml")

        val error = assertFailsWith<IllegalStateException> { ApplicationConfigLoader.load(path) }

        assertTrue(error.message!!.contains(path.toString()))
    }

    @Test
    fun `rejects a config without an llm section`() {
        val error = assertFailsWith<IllegalStateException> {
            ApplicationConfigLoader.parse("server:\n  port: 9000")
        }

        assertTrue(error.message!!.startsWith("Invalid application config"))
    }

    @Test
    fun `rejects blank values when building the adapter config`() {
        val config = ApplicationConfigLoader.parse(
            """
            llm:
              baseUrl: " "
              model: some-model
              apiKeyEnv: DEEPSEEK_KEY
            """.trimIndent(),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            config.toOpenAiCompatibleConfig(mapOf("DEEPSEEK_KEY" to "secret"))
        }

        assertEquals("baseUrl must not be blank", error.message)
    }

    @Test
    fun `rejects a blank api key variable name`() {
        val error = assertFailsWith<IllegalStateException> {
            ApplicationConfigLoader.parse(
                """
                llm:
                  baseUrl: https://api.example.com/v1
                  model: some-model
                  apiKeyEnv: " "
                """.trimIndent(),
            )
        }

        assertTrue(error.message!!.startsWith("Invalid application config"))
    }

    @Test
    fun `resolves the api key from the environment for the adapter`() {
        val config = ApplicationConfigLoader.parse(
            """
            llm:
              baseUrl: https://api.deepseek.com/v1
              model: deepseek-chat
              apiKeyEnv: DEEPSEEK_KEY
            """.trimIndent(),
        )

        val adapterConfig = config.toOpenAiCompatibleConfig(mapOf("DEEPSEEK_KEY" to "secret"))

        assertEquals("https://api.deepseek.com/v1", adapterConfig.baseUrl)
        assertEquals("deepseek-chat", adapterConfig.model)
        assertEquals("secret", adapterConfig.apiKey)
    }

    @Test
    fun `explains how to inject a missing api key from 1Password`() {
        val config = ApplicationConfig(
            llm = ApplicationConfig.LlmConfig(baseUrl = "https://api.example.com/v1", model = "model"),
        )

        val error = assertFailsWith<IllegalStateException> { config.toOpenAiCompatibleConfig(emptyMap()) }

        assertTrue(error.message!!.contains("LLM_API_KEY"))
        assertTrue(error.message!!.contains("op run"))
    }
}
