package com.marketbrief

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    val port = resolvePort(System.getenv("PORT"))
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

internal fun resolvePort(raw: String?): Int {
    if (raw == null) return 8080
    val port = raw.toIntOrNull()
    require(port != null && port in 1..65535) {
        "PORT must be an integer between 1 and 65535, got '$raw'"
    }
    return port
}

fun Application.module() {
    routing {
        get("/health") {
            call.respondText("ok")
        }
    }
}
