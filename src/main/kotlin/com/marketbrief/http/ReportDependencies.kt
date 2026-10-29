package com.marketbrief.http

import com.marketbrief.application.GenerateDailyReport
import com.marketbrief.domain.ReportStore

data class ReportDependencies(val generateDailyReport: GenerateDailyReport, val reportStore: ReportStore)
