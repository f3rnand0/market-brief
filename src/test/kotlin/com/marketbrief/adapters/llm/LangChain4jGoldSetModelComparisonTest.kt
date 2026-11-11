package com.marketbrief.adapters.llm

import com.marketbrief.adapters.news.JsonCorpusNewsSource
import com.marketbrief.adapters.news.JsonGoldSet
import com.marketbrief.application.RunEval
import com.marketbrief.domain.EvalReport
import com.marketbrief.fakes.InMemoryEvalStore
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.openai.OpenAiChatModel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

@Tag("live")
class LangChain4jGoldSetModelComparisonTest {
    @Test
    fun `compares OpenCode models on the Gold set`() {
        runBlocking {
            val token = System.getenv("OPENCODE_API_KEY") ?: error("OPENCODE_API_KEY is required")
            val judgeRates = TOKEN_RATES.getValue(CLAIM_SUPPORT_JUDGE_MODEL)
            val judge = LangChain4jClaimSupportJudge(
                chatModel(token, CLAIM_SUPPORT_JUDGE_MODEL),
                inputCostPerMillionUsd = judgeRates.inputUsdPerMillion,
                outputCostPerMillionUsd = judgeRates.outputUsdPerMillion,
            )
            val corpus = JsonCorpusNewsSource.fromResource()
            val goldSet = JsonGoldSet.fromResource()
            val results = EXTRACTION_MODELS.map { model ->
                val extractionRates = TOKEN_RATES.getValue(model)
                val extractor = LangChain4jNewsItemExtractor(
                    chatModel(token, model),
                    inputCostPerMillionUsd = extractionRates.inputUsdPerMillion,
                    outputCostPerMillionUsd = extractionRates.outputUsdPerMillion,
                )
                val store = InMemoryEvalStore()
                val report = RunEval(corpus, extractor, store, goldSet, judge).run()
                assertTrue(report.metrics.getValue("expectedItems") > 0, "Gold set must contain expected NewsItems")
                assertTrue(report.metrics.containsKey("precision"), "Eval report must include item precision")
                model to report
            }

            val reportPath = Path.of(REPORT_PATH)
            Files.createDirectories(reportPath.parent ?: Path.of("."))
            Files.writeString(reportPath, renderReport(CLAIM_SUPPORT_JUDGE_MODEL, goldSet.cases.size, results))
        }
    }

    private fun chatModel(token: String, model: String): ChatModel = OpenAiChatModel.builder()
        .baseUrl(BASE_URL)
        .apiKey(token)
        .modelName(model)
        .timeout(Duration.ofSeconds(120))
        .maxRetries(2)
        .customHeaders(
            mapOf(
                "User-Agent" to USER_AGENT,
                "x-opencode-session" to UUID.randomUUID().toString(),
            ),
        )
        .build()

    private fun renderReport(judgeModel: String, articleCount: Int, results: List<Pair<String, EvalReport>>): String =
        buildString {
            appendLine("# LangChain4j OpenCode Gold-set comparison")
            appendLine()
            appendLine("Articles: $articleCount.")
            appendLine("Claim-support judge: `$judgeModel` (shared by every extraction model).")
            appendLine("Each model was evaluated with the production `RunEval` pipeline.")
            appendLine()
            appendLine(
                "| Model | Precision | Recall | Field precision | Field recall | Missing values | " +
                    "Supported claims | Unsupported claims | Est. cost (USD) |",
            )
            appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
            results.forEach { (model, report) ->
                val metrics = report.metrics
                appendLine(
                    "| $model | ${metrics["precision"]} | ${metrics["recall"]} | " +
                        "${metrics["accuracy.precision"]} | ${metrics["accuracy.recall"]} | " +
                        "${metrics["missingValues"]} | ${metrics["supportedClaims"]} | " +
                        "${metrics["unsupportedClaims"]} | ${metrics["cost.estimatedUsd"] ?: "unavailable"} |",
                )
            }
            appendLine()
            appendLine("## Full metrics")
            results.forEach { (model, report) ->
                appendLine()
                appendLine("### $model")
                appendLine()
                report.metrics.toSortedMap().forEach { (name, value) -> appendLine("- `$name`: $value") }
                appendLine()
                appendLine(report.markdown)
            }
            appendLine()
            appendLine(
                "Interpretation: this is a same-corpus model comparison, not a new scoring implementation. " +
                    "The Gold-set metrics are exact/field comparisons to human labels; claim-support scores use the " +
                    "shared judge model and should be reviewed using the generated Eval report samples.",
            )
        }

    private companion object {
        const val BASE_URL = "https://opencode.ai/inference/go/openai/v1"
        const val USER_AGENT = "market-brief/0.1.0"
        const val CLAIM_SUPPORT_JUDGE_MODEL = "minimax-m3"
        const val REPORT_PATH = "build/reports/langchain4j-gold-set.md"
        val EXTRACTION_MODELS = listOf("deepseek-v4.1-flash", "glm-5.3-flash")
        val TOKEN_RATES = mapOf(
            "deepseek-v4.1-flash" to TokenRates(0.15, 0.60),
            "glm-5.3-flash" to TokenRates(0.15, 0.50),
            "minimax-m3" to TokenRates(0.30, 1.20),
        )
    }

    private data class TokenRates(val inputUsdPerMillion: Double, val outputUsdPerMillion: Double)
}
