package com.marketbrief.adapters.news

import com.marketbrief.domain.DayWindow
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonCorpusNewsSourceTest {

    @Test
    fun `loads the curated corpus from the packaged resource`() {
        val articles = JsonCorpusNewsSource.fromResource().fetch(DayWindow(LocalDate.of(2026, 10, 25)))

        assertTrue(articles.size >= 5, "the PoC corpus needs enough articles to fill every section")
        assertTrue(articles.map { it.id }.toSet().size == articles.size, "article ids must be unique")
        assertTrue(articles.all { it.headline.isNotBlank() && it.body.isNotBlank() && it.outlet.isNotBlank() })
        assertEquals(articles, JsonCorpusNewsSource.fromResource().fetch(DayWindow(LocalDate.of(2026, 10, 26))))
    }
}
