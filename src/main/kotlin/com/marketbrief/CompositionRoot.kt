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
import com.marketbrief.scheduling.DailyReportScheduler
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.nio.file.Path
import java.time.Clock

object CompositionRoot {

    fun build(
        config: ApplicationConfig = ApplicationConfigLoader.load(),
        env: Map<String, String> = System.getenv(),
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
        return ReportDependencies(generateDailyReport, reportStore)
    }

    fun scheduler(generateDailyReport: GenerateDailyReport, clock: Clock = Clock.systemUTC()): DailyReportScheduler =
        DailyReportScheduler(
            clock = clock,
            zone = DailyReportScheduler.REPORT_ZONE,
            triggerTime = DailyReportScheduler.REPORT_TIME,
            action = generateDailyReport::generate,
        )
}

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
