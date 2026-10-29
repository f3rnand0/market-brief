package com.marketbrief.http

import com.marketbrief.reportformat.NewsItemDto
import com.marketbrief.reportformat.ReportDto
import com.marketbrief.reportformat.SnapshotGroupDto
import kotlinx.html.FlowContent
import kotlinx.html.body
import kotlinx.html.cite
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.lang
import kotlinx.html.li
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.title
import kotlinx.html.tr
import kotlinx.html.ul
import kotlinx.html.unsafe

object ReportHtml {

    fun render(report: ReportDto): String = createHTML().html {
        lang = "en"
        head {
            meta { charset = "utf-8" }
            meta {
                name = "viewport"
                content = "width=device-width, initial-scale=1"
            }
            title("Market brief — ${report.date}")
            style { unsafe { +STYLE } }
        }
        body {
            h1 { +"Market brief — ${report.date}" }
            section {
                h2 { +"Summary" }
                p { +report.summary }
            }
            snapshot(report.snapshot)
            regionalNews(report.regionalNews)
            newsSection("Company / sector news", report.companySectorNews)
            newsSection("Other important news", report.otherNews)
        }
    }

    private fun FlowContent.snapshot(groups: List<SnapshotGroupDto>) {
        section {
            h2 { +"Market snapshot" }
            if (groups.isEmpty()) {
                p { +"No market data available." }
            } else {
                groups.forEach { group -> assetClassSection(group) }
            }
        }
    }

    private fun FlowContent.assetClassSection(group: SnapshotGroupDto) {
        h3 { +assetClassLabel(group.assetClass) }
        table {
            thead {
                tr {
                    th { +"Instrument" }
                    th { +"Value and change" }
                }
            }
            tbody {
                group.quotes.forEach { quote ->
                    tr {
                        td { +quote.instrument }
                        td { +quote.display }
                    }
                }
                group.missing.forEach { name ->
                    tr {
                        td { +name }
                        td { +"data unavailable" }
                    }
                }
            }
        }
    }

    private fun FlowContent.regionalNews(regionalNews: Map<String, List<NewsItemDto>>) {
        section {
            h2 { +"Regional news" }
            if (regionalNews.isEmpty()) {
                p { +"No regional news." }
            } else {
                regionalNews.forEach { (region, items) ->
                    h3 { +regionLabel(region) }
                    newsList(items)
                }
            }
        }
    }

    private fun FlowContent.newsSection(title: String, items: List<NewsItemDto>) {
        section {
            h2 { +title }
            if (items.isEmpty()) {
                p { +"No items." }
            } else {
                newsList(items)
            }
        }
    }

    private fun FlowContent.newsList(items: List<NewsItemDto>) {
        ul {
            items.forEach { item ->
                li {
                    span { +item.assertion }
                    if (item.subjects.isNotEmpty()) {
                        span { +" — ${item.subjects.joinToString(", ")}" }
                    }
                    if (item.metrics.isNotEmpty()) {
                        val metrics = item.metrics.joinToString(", ") { "${it.value} ${it.unit}" }
                        span { +" ($metrics)" }
                    }
                    cite { +" [source: ${item.source.articleId}]" }
                }
            }
        }
    }

    private fun regionLabel(region: String): String = when (region) {
        "US" -> "US"
        else -> region.lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun assetClassLabel(assetClass: String): String = when (assetClass) {
        "EQUITIES" -> "Equities"
        "FIXED_INCOME" -> "Fixed income"
        "FX_AND_VOLATILITY" -> "FX & volatility"
        "COMMODITIES" -> "Commodities"
        "CRYPTO" -> "Crypto"
        else -> assetClass
    }

    private const val STYLE = """
        body { font-family: system-ui, sans-serif; max-width: 60rem; margin: 2rem auto; padding: 0 1rem; }
        table { border-collapse: collapse; width: 100%; margin-bottom: 1rem; }
        th, td { text-align: left; padding: .25rem .5rem; border-bottom: 1px solid #ddd; }
        li { margin-bottom: .35rem; }
    """
}
