package com.marketbrief.domain

enum class ClaimSupport { SUPPORTED, UNSUPPORTED, INDETERMINATE }

data class ClaimSupportJudgment(val support: ClaimSupport, val usage: ExtractionUsage? = null)
