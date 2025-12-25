package com.autoparts

import com.autoparts.db.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import com.autoparts.routes.registerAdminRoutes

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureMonitoring()
    configureSecurity()
    configureTemplating()
    configureRouting()
    registerAdminRoutes()

    DatabaseFactory.init(environment.config)
    com.autoparts.seed.DataSeeder.seedIfEmpty()

}
