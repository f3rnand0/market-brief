package com.marketbrief

import com.marketbrief.http.ReportDependencies
import com.marketbrief.http.reportRoutes
import com.marketbrief.reportformat.ReportMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val port = resolvePort(System.getenv("PORT"))
    val httpClient = HttpClient(CIO)
    try {
        val dependencies = CompositionRoot.build(httpClient = httpClient)
        embeddedServer(Netty, port = port, host = "0.0.0.0") { module(dependencies) }
            .start(wait = true)
    } finally {
        httpClient.close()
    }
}

internal fun resolvePort(raw: String?): Int {
    if (raw == null) return 8080
    val port = raw.toIntOrNull()
    require(port != null && port in 1..65535) {
        "PORT must be an integer between 1 and 65535, got '$raw'"
    }
    return port
}

fun Application.module(dependencies: ReportDependencies? = null) {
    install(ContentNegotiation) {
        json(ReportMapper.format)
    }
    routing {
        get("/health") {
            call.respondText("ok")
        }
        if (dependencies != null) {
            reportRoutes(dependencies)
        }
    }
}
