package com.marketbrief.http

import com.marketbrief.reportformat.ErrorDto
import com.marketbrief.reportformat.toDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.evalRoutes(dependencies: ReportDependencies) {
    post("/eval/run") {
        val runner = dependencies.runEval
            ?: return@post call.respond(HttpStatusCode.NotImplemented, ErrorDto("Evaluation is not configured"))
        call.respond(runner.run().toDto())
    }
    get("/eval/{id}") {
        val id = call.parameters["id"]
        val report = if (id.isNullOrBlank()) null else dependencies.evalStore?.load(id)
        if (report == null) {
            call.respond(HttpStatusCode.NotFound, ErrorDto("No evaluation for $id"))
        } else {
            call.respond(report.toDto())
        }
    }
}
