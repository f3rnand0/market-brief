package com.marketbrief.domain

enum class ClaimSupport { SUPPORTED, UNSUPPORTED, INDETERMINATE }

data class ClaimSupportContext(val headline: String, val articleText: String)

data class ClaimSupportJudgment(val support: ClaimSupport, val usage: ExtractionUsage? = null)
