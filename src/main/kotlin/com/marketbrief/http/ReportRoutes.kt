package com.marketbrief.http

import com.marketbrief.wire.ErrorDto
import com.marketbrief.wire.ReportMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException

fun Route.reportRoutes(dependencies: ReportDependencies) {
    post("/report/generate") {
        val date = call.generateDateOrRespond(dependencies.clock) ?: return@post
        val report = dependencies.generateDailyReport.generate(date)
        call.respond(ReportMapper.toDto(report))
    }

    get("/report/{date}.html") {
        val date = call.pathDateOrRespond() ?: return@get
        val report = dependencies.reportStore.load(date)
        if (report == null) {
            call.respond(HttpStatusCode.NotFound, ErrorDto("No report for $date"))
        } else {
            call.respondText(ReportHtml.render(ReportMapper.toDto(report)), ContentType.Text.Html)
        }
    }

    get("/report/{date}") {
        val date = call.pathDateOrRespond() ?: return@get
        val report = dependencies.reportStore.load(date)
        if (report == null) {
            call.respond(HttpStatusCode.NotFound, ErrorDto("No report for $date"))
        } else {
            call.respond(ReportMapper.toDto(report))
        }
    }
}

private suspend fun ApplicationCall.generateDateOrRespond(clock: Clock): LocalDate? {
    val raw = request.queryParameters["date"] ?: return LocalDate.now(clock)
    return parseDateOrRespond(raw)
}

private suspend fun ApplicationCall.pathDateOrRespond(): LocalDate? = parseDateOrRespond(parameters["date"])

private suspend fun ApplicationCall.parseDateOrRespond(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) {
        respond(HttpStatusCode.BadRequest, ErrorDto("A report date is required"))
        return null
    }
    return try {
        LocalDate.parse(raw)
    } catch (e: DateTimeParseException) {
        respond(HttpStatusCode.BadRequest, ErrorDto("Invalid report date '$raw'; expected YYYY-MM-DD"))
        null
    }
}
