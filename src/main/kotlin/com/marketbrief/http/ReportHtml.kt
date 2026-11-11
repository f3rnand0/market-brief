package com.marketbrief.http

import com.marketbrief.reportformat.NewsItemDto
import com.marketbrief.reportformat.QuoteDto
import com.marketbrief.reportformat.ReportDto
import com.marketbrief.reportformat.SnapshotGroupDto
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.article
import kotlinx.html.body
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.html
import kotlinx.html.lang
import kotlinx.html.li
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.summary
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe

object ReportHtml {

    fun render(report: ReportDto): String {
        val indexQuotes = indexBoard(report.snapshot)
        return createHTML().html {
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
                div("page-shell") {
                    header("masthead") {
                        div("brand-lockup") {
                            span("brand-mark") { +"M" }
                            div {
                                span("brand-name") { +"MARKET BRIEF" }
                                span("brand-caption") { +"DAILY EDITION · GLOBAL MARKETS" }
                            }
                        }
                        span("report-date") { +report.date }
                    }
                    nav("report-nav") {
                        a("#market-pulse") { +"Market pulse" }
                        a("#regional-desk") { +"Regional desks" }
                        a("#company-watch") { +"Company watch" }
                        a("#cross-asset") { +"Cross-asset" }
                    }
                    main {
                        div("dashboard") {
                            section("index-rail") {
                                attributes["id"] = "market-pulse"
                                h2 { +"Index board" }
                                span("rail-caption") { +report.date }
                                indexQuotes.forEach { (quote, assetClass) -> marketEntry(quote, assetClass) }
                                if (indexQuotes.isEmpty()) {
                                    p("empty-state") { +"No index data available." }
                                }
                            }
                            section("headline-panel") {
                                attributes["id"] = "daily-summary"
                                span("eyebrow") { +"MARKET CLOSE · PREVIOUS DAY" }
                                h1 { +"The day in focus" }
                                p("summary-copy") { +report.summary }
                            }
                        }
                        section("report-section") {
                            sectionHeading("Desk notes", "Regional developments and company coverage")
                            div("desk-columns") {
                                section("desk-group regional-desk") {
                                    attributes["id"] = "regional-desk"
                                    groupHeading("Regional desk", "Max 4 per region")
                                    regionalNews(report.regionalNews)
                                }
                                section("desk-group company-desk") {
                                    attributes["id"] = "company-watch"
                                    groupHeading("Company tape", "Distinct company subjects")
                                    newsList(report.companySectorNews)
                                }
                            }
                        }
                        section("report-section cross-asset-section") {
                            attributes["id"] = "cross-asset"
                            sectionHeading("Cross-asset monitor", "Latest level and daily move")
                            snapshot(report.snapshot, indexQuotes.map { it.first.instrument }.toSet())
                        }
                        section("report-section other-section") {
                            sectionHeading("Other important news", "Global macro and market developments")
                            newsList(report.otherNews)
                        }
                    }
                    footer("report-footer") { +"Market data desk · Cited reporting · ${report.date}" }
                }
            }
        }
    }

    private fun FlowContent.sectionHeading(title: String, caption: String) {
        div("section-heading") {
            h2 { +title }
            span { +caption }
        }
    }

    private fun FlowContent.groupHeading(title: String, caption: String) {
        div("group-heading") {
            h3 { +title }
            span("group-caption") { +caption }
        }
    }

    private fun indexBoard(groups: List<SnapshotGroupDto>): List<Pair<QuoteDto, String>> = groups
        .firstOrNull { it.assetClass == EQUITIES }
        ?.quotes
        .orEmpty()
        .take(INDEX_BOARD_SIZE)
        .map { quote -> quote to EQUITIES }

    private fun FlowContent.marketEntry(quote: QuoteDto, assetClass: String) {
        div("market-entry") {
            span("market-label") { +quote.instrument }
            div("market-reading") {
                span("market-value") { +quote.value }
                span(changeClass(quote.change, assetClass)) { +changeText(quote) }
            }
        }
    }

    private fun FlowContent.snapshot(groups: List<SnapshotGroupDto>, indexBoardInstruments: Set<String>) {
        val visibleGroups = groups.mapNotNull { group ->
            val visibleQuotes = group.quotes.filterNot { it.instrument in indexBoardInstruments }
            group.takeIf { visibleQuotes.isNotEmpty() || it.missing.isNotEmpty() }
                ?.let { it to visibleQuotes }
        }
        if (visibleGroups.isEmpty()) {
            p("empty-state") { +"No additional market data available." }
        } else {
            div("asset-grid") {
                visibleGroups.forEach { (group, quotes) -> assetClassSection(group.assetClass, quotes, group.missing) }
            }
        }
    }

    private fun FlowContent.assetClassSection(assetClass: String, quotes: List<QuoteDto>, missing: List<String>) {
        section("asset-class") {
            h3 { +assetClassLabel(assetClass) }
            ul("asset-list") {
                quotes.forEach { quote ->
                    li("asset-row") {
                        span { +quote.instrument }
                        span("asset-reading") {
                            span("asset-value") { +quote.value }
                            span(changeClass(quote.change, assetClass)) { +changeText(quote) }
                        }
                    }
                }
                missing.forEach { name ->
                    li("asset-row") {
                        span { +name }
                        span("unavailable") { +"Data unavailable" }
                    }
                }
            }
        }
    }

    private fun FlowContent.regionalNews(regionalNews: Map<String, List<NewsItemDto>>) {
        if (regionalNews.isEmpty()) {
            p("empty-state") { +"No regional news." }
        } else {
            regionalNews.forEach { (region, items) ->
                div("regional-group") {
                    div("regional-heading") {
                        h3 { +regionLabel(region) }
                        span("item-count") { +"${items.size} stories" }
                    }
                    newsList(items)
                }
            }
        }
    }

    private fun FlowContent.newsList(items: List<NewsItemDto>) {
        if (items.isEmpty()) {
            p("empty-state") { +"No items selected for this section." }
        } else {
            ul("news-list") {
                items.forEach { item ->
                    li {
                        article("news-item") {
                            div("story-meta") {
                                span("story-tag ${typeClass(item.type)}") { +typeLabel(item.type) }
                                span("story-region") { +regionLabel(item.region) }
                            }
                            h3 { +item.assertion }
                            if (item.subjects.isNotEmpty()) {
                                p("story-subjects") { +item.subjects.joinToString(" · ") }
                            }
                            if (item.metrics.isNotEmpty()) {
                                div("metrics") {
                                    item.metrics.forEach { metric ->
                                        span("metric") { +"${metric.value} ${metric.unit}" }
                                    }
                                }
                            }
                            details("citation") {
                                summary { +"Open citation · ${item.source.articleId}" }
                                item.source.quotes.forEach { quote -> p("citation-quote") { +quote } }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun changeText(quote: QuoteDto): String = quote.display
        .substringAfter(quote.value, "")
        .trim()
        .ifBlank { "—" }

    private fun regionLabel(region: String): String = when (region) {
        "US" -> "United States"
        else -> region.lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun typeLabel(type: String): String = when (type) {
        "MARKET_MOVE" -> "Market move"
        else -> type.lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun typeClass(type: String): String = when (type) {
        "COMPANY" -> "tag-company"
        "SECTOR" -> "tag-sector"
        "MACRO" -> "tag-macro"
        else -> "tag-market"
    }

    private fun assetClassLabel(assetClass: String): String = when (assetClass) {
        "EQUITIES" -> "Equities"
        "FIXED_INCOME" -> "Fixed income"
        "FX_AND_VOLATILITY" -> "FX & volatility"
        "COMMODITIES" -> "Commodities"
        "CRYPTO" -> "Crypto"
        else -> assetClass
    }

    private fun changeClass(change: String, assetClass: String): String {
        if (assetClass == "FX_AND_VOLATILITY") return "change-neutral"
        val sign = change.toBigDecimalOrNull()?.signum() ?: return "change-neutral"
        val marketDirection = if (assetClass == "FIXED_INCOME") -sign else sign
        return when (marketDirection) {
            1 -> "change-up"
            -1 -> "change-down"
            else -> "change-neutral"
        }
    }

    private const val EQUITIES = "EQUITIES"
    private const val INDEX_BOARD_SIZE = 4

    private const val STYLE = """
        :root {
            color-scheme: light dark;
            --page: light-dark(#edf2f8, #111a26);
            --paper: light-dark(#fff, #1a2838);
            --ink: light-dark(#19283a, #eef3f8);
            --muted: light-dark(#64768a, #a9bacb);
            --line: light-dark(#d9e2ec, #35495e);
            --orange: light-dark(#e16d3c, #ff9867);
            --mint: light-dark(#168777, #56d0b5);
            --purple: light-dark(#6557b7, #aa9cf5);
            --blue: light-dark(#2769b1, #6aa9eb);
            --red: light-dark(#ad4848, #f09b9b);
        }
        * { box-sizing: border-box; }
        body { margin: 0; color: var(--ink); background: var(--page);
            font: 15px/1.55 Inter, ui-sans-serif, system-ui, -apple-system, sans-serif; }
        .page-shell { max-width: 1160px; margin: 0 auto; padding: 0 28px 34px; }
        .masthead { display: flex; align-items: center; justify-content: space-between; gap: 16px;
            padding: 19px 0 15px; border-bottom: 1px solid var(--line); }
        .brand-lockup { display: flex; align-items: center; gap: 11px; }
        .brand-mark { display: grid; place-items: center; width: 37px; height: 37px; border-radius: 11px;
            color: #fff; background: var(--orange); font-size: 15px; font-weight: 750; }
        .brand-name, .brand-caption { display: block; }
        .brand-name { font-size: 12px; font-weight: 750; letter-spacing: .13em; }
        .brand-caption, .report-date { color: var(--muted); font-size: 11px; letter-spacing: .06em; }
        .report-date { padding: 6px 10px; border: 1px solid var(--line); border-radius: 999px;
            background: var(--paper); font-variant-numeric: tabular-nums; }
        .report-nav { display: flex; flex-wrap: wrap; gap: 20px; padding: 11px 0;
            border-bottom: 1px solid var(--line); }
        .report-nav a { color: var(--blue); font-size: 12px; font-weight: 600; text-decoration: none; }
        .report-nav a:hover { text-decoration: underline; }
        main { padding-top: 20px; }
        .dashboard { display: grid; grid-template-columns: 230px minmax(0, 1fr); gap: 20px; align-items: stretch; }
        .index-rail { padding: 15px; background: var(--paper); border-left: 3px solid var(--orange); border-radius: 12px; }
        .index-rail h2 { margin: 0; color: var(--blue); font-size: 13px; }
        .rail-caption { color: var(--muted); font-size: 11px; }
        .market-entry { padding: 9px 0; border-top: 1px solid var(--line); }
        .market-label { display: block; color: var(--muted); font-size: 12px; }
        .market-reading { display: flex; flex-wrap: wrap; align-items: baseline; gap: 7px; }
        .market-value { font-size: 15px; font-weight: 700; font-variant-numeric: tabular-nums; }
        .change-up, .change-down, .change-neutral { font-size: 11px; font-weight: 650; white-space: nowrap; }
        .change-up { color: var(--mint); }
        .change-down { color: var(--red); }
        .change-neutral, .unavailable { color: var(--muted); }
        .headline-panel { display: flex; flex-direction: column; justify-content: center; min-height: 240px;
            padding: 24px 27px; border-radius: 13px; color: #fff;
            background: linear-gradient(120deg, #253f72, #674c94 66%, #a55362); }
        .eyebrow { color: #ffd29a; font-size: 11px; font-weight: 750; letter-spacing: .12em; }
        h1 { margin: 7px 0 10px; max-width: 700px; font-size: clamp(25px, 3.5vw, 35px);
            line-height: 1.16; letter-spacing: -.025em; }
        .summary-copy { max-width: 760px; margin: 0; color: #f0edf8; font-size: 15px; }
        .report-section { margin-top: 27px; }
        .section-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 12px;
            margin-bottom: 12px; padding-bottom: 7px; border-bottom: 1px solid var(--line); }
        .section-heading h2 { margin: 0; font-size: 19px; letter-spacing: -.02em; }
        .section-heading span { color: var(--muted); font-size: 11px; }
        .desk-columns { display: grid; grid-template-columns: 1.1fr .9fr; gap: 14px; align-items: start; }
        .desk-group { min-width: 0; padding: 14px; background: var(--paper); border-radius: 12px; }
        .group-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px;
            padding-bottom: 10px; border-bottom: 1px solid var(--line); }
        .group-heading h3 { margin: 0; font-size: 14px; }
        .group-caption, .item-count { color: var(--muted); font-size: 11px; }
        .regional-group { padding-top: 10px; }
        .regional-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
        .regional-heading h3 { margin: 0; color: var(--blue); font-size: 12px; }
        .news-list { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }
        .regional-group .news-list { gap: 0; }
        .news-item { padding: 12px 0; border-bottom: 1px solid var(--line); }
        .news-list li:last-child .news-item { border-bottom: 0; }
        .story-meta { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; }
        .story-tag { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 10px;
            font-weight: 700; letter-spacing: .04em; text-transform: uppercase; }
        .tag-company { color: var(--orange); background: light-dark(#fae9e2, #493129); }
        .tag-sector { color: var(--purple); background: light-dark(#eeeafd, #352f58); }
        .tag-macro { color: var(--blue); background: light-dark(#e6f0fa, #24394d); }
        .tag-market { color: var(--mint); background: light-dark(#e0f3ee, #203d39); }
        .story-region { color: var(--muted); font-size: 11px; }
        .news-item h3 { margin: 6px 0 3px; font-size: 13px; line-height: 1.42; font-weight: 650; }
        .story-subjects { margin: 0; color: var(--muted); font-size: 11px; }
        .metrics { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 7px; }
        .metric { padding: 2px 7px; border-radius: 5px; color: var(--ink); background: var(--page);
            font-size: 11px; font-variant-numeric: tabular-nums; }
        .citation { margin-top: 7px; color: var(--muted); font-size: 11px; }
        .citation summary { color: var(--blue); cursor: pointer; }
        .citation-quote { margin: 7px 0 0; padding-left: 10px; border-left: 2px solid var(--purple); }
        .asset-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 11px; }
        .asset-class { min-width: 0; padding: 12px 14px; background: var(--paper); border-radius: 10px;
            border-top: 2px solid var(--mint); }
        .asset-class:nth-child(3n + 2) { border-color: var(--orange); }
        .asset-class:nth-child(3n) { border-color: var(--purple); }
        .asset-class h3 { margin: 0 0 7px; color: var(--blue); font-size: 12px; }
        .asset-list { margin: 0; padding: 0; list-style: none; }
        .asset-row { display: flex; justify-content: space-between; align-items: baseline; gap: 9px;
            padding: 6px 0; border-top: 1px solid var(--line); font-size: 11px; }
        .asset-reading { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 6px; text-align: right; }
        .asset-value { font-weight: 650; font-variant-numeric: tabular-nums; }
        .empty-state { margin: 0; padding: 10px 0; color: var(--muted); font-size: 12px; }
        .report-footer { margin-top: 30px; padding: 12px 0; border-top: 1px solid var(--line);
            color: var(--muted); font-size: 11px; }
        @media (max-width: 760px) {
            .page-shell { padding: 0 15px 24px; }
            .dashboard, .desk-columns { grid-template-columns: 1fr; }
            .headline-panel { min-height: 190px; }
            .index-rail { order: 2; }
            .headline-panel { order: 1; }
            .section-heading { display: block; }
            .section-heading span { display: block; margin-top: 3px; }
        }
    """
}
