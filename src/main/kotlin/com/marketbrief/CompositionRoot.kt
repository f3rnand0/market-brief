package com.marketbrief

import com.marketbrief.adapters.llm.LangChain4jNewsItemExtractor
import com.marketbrief.adapters.llm.OpenAiCompatibleConfig
import com.marketbrief.adapters.marketdata.CnbcQuoteFetcher
import com.marketbrief.adapters.marketdata.CompositeMarketDataFetcher
import com.marketbrief.adapters.marketdata.YahooQuoteFetcher
import com.marketbrief.adapters.news.JsonCorpusNewsSource
import com.marketbrief.adapters.report.JsonReportStore
import com.marketbrief.application.GenerateDailyReport
import com.marketbrief.config.ApplicationConfig
import com.marketbrief.config.ApplicationConfigLoader
import com.marketbrief.domain.AssetClass
import com.marketbrief.domain.ReportInstruments
import com.marketbrief.http.ReportDependencies
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneId

/**
 * The only place that constructs adapters and hands them to the use case
 * (ARCHITECTURE §1). Tests build the same wiring with fakes.
 */
object CompositionRoot {

    fun build(
        config: ApplicationConfig = ApplicationConfigLoader.load(),
        env: Map<String, String> = System.getenv(),
        clock: Clock = Clock.system(ZoneId.of("Europe/Berlin")),
        httpClient: HttpClient = HttpClient(CIO),
    ): ReportDependencies {
        val marketDataFetcher = CompositeMarketDataFetcher(
            primary = YahooQuoteFetcher(httpClient),
            fallbacks = mapOf(AssetClass.FIXED_INCOME to listOf(CnbcQuoteFetcher(httpClient))),
        )
        val reportStore = JsonReportStore(Path.of(config.storage.reportsDir))
        val generateDailyReport = GenerateDailyReport(
            marketDataFetcher = marketDataFetcher,
            newsSource = JsonCorpusNewsSource.fromResource(),
            extractor = LangChain4jNewsItemExtractor.fromConfig(config.toOpenAiCompatibleConfig(env)),
            reportStore = reportStore,
            instruments = ReportInstruments.all,
        )
        return ReportDependencies(generateDailyReport, reportStore, clock)
    }
}

/**
 * Composition-root boundary: only `main` may turn runtime config into an
 * adapter config, so the config package stays adapter-agnostic (ARCHITECTURE §1).
 */
fun ApplicationConfig.toOpenAiCompatibleConfig(env: Map<String, String> = System.getenv()): OpenAiCompatibleConfig {
    val apiKey = env[llm.apiKeyEnv]?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException(
            "Missing LLM API key: environment variable ${llm.apiKeyEnv} is not set. " +
                "Inject it with 1Password: op run --env-file=config/secrets.env -- <command>",
        )
    return OpenAiCompatibleConfig(
        baseUrl = llm.baseUrl,
        model = llm.model,
        apiKey = apiKey,
        requestTimeoutSeconds = llm.requestTimeoutSeconds,
        maxRetries = llm.maxRetries,
    )
}
