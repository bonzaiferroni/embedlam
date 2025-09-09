package ponder.embedlam.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import klutch.server.*
import ponder.embedlam.model.apiPrefix
import ponder.embedlam.server.routes.*

fun Application.configureApiRoutes() {
    routing {
        get(apiPrefix) {
            call.respondText("Hello World!")
        }

        serveUsers()
        serveExamples()
    }
}