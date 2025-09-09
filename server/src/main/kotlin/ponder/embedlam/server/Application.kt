package ponder.embedlam.server

import io.ktor.server.application.*
import klutch.server.configureSecurity
import ponder.embedlam.server.plugins.configureApiRoutes
import ponder.embedlam.server.plugins.configureCors
import ponder.embedlam.server.plugins.configureDatabases
import ponder.embedlam.server.plugins.configureLogging
import ponder.embedlam.server.plugins.configureSerialization
import ponder.embedlam.server.plugins.configureWebSockets

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureCors()
    configureSerialization()
    configureDatabases()
    configureSecurity()
    configureApiRoutes()
    configureWebSockets()
    configureLogging()
}