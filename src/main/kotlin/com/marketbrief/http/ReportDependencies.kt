package com.marketbrief.http

import com.marketbrief.application.GenerateDailyReport
import com.marketbrief.application.RunEval
import com.marketbrief.domain.EvalStore
import com.marketbrief.domain.ReportStore

data class ReportDependencies(
    val generateDailyReport: GenerateDailyReport,
    val reportStore: ReportStore,
    val runEval: RunEval? = null,
    val evalStore: EvalStore? = null,
)
