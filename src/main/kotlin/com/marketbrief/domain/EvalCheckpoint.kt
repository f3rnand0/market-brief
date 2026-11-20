package com.marketbrief.domain

data class EvalCheckpoint(
    val goldSetVersion: String,
    val articleIds: List<String>,
    val extractions: Map<String, NewsItemExtraction>,
)
