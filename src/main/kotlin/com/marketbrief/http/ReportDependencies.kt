package com.marketbrief.http

import com.marketbrief.application.GenerateDailyReport
import com.marketbrief.domain.ReportStore
import java.time.Clock

/** What the HTTP layer needs; `main` supplies the production adapters. */
data class ReportDependencies(
    val generateDailyReport: GenerateDailyReport,
    val reportStore: ReportStore,
    val clock: Clock = Clock.systemUTC(),
)
