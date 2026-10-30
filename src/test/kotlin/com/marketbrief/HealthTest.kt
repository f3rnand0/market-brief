package com.marketbrief

import com.marketbrief.application.GenerateDailyReport
import com.marketbrief.fakes.DeterministicNewsItemExtractor
import com.marketbrief.fakes.FakeMarketDataFetcher
import com.marketbrief.fakes.InMemoryReportStore
import com.marketbrief.fakes.StaticCorpusNewsSource
import com.marketbrief.http.ReportDependencies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthTest {
    @Test
    fun `health returns ok`() = testApplication {
        application { module(dependencies()) }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    private fun dependencies(): ReportDependencies {
        val store = InMemoryReportStore()
        return ReportDependencies(
            generateDailyReport = GenerateDailyReport(
                marketDataFetcher = FakeMarketDataFetcher(),
                newsSource = StaticCorpusNewsSource(emptyList()),
                extractor = DeterministicNewsItemExtractor(emptyMap()),
                reportStore = store,
                instruments = emptySet(),
            ),
            reportStore = store,
        )
    }
}
