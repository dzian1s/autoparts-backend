package com.autoparts

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.autoparts.repo.ProductRepository

fun Application.configureRouting() {
    val repo = ProductRepository()
    routing {
        route("/api"){

            get("/health") {
                call.respondText("OK")
            }

            get("/products"){
                val items = repo.list(limit = 100, offset = 0) // пока без пагинации
                call.respond(items)
            }

            get("/products/{id}") {
                val id = call.parameters["id"]
                if (id == null) {
                    call.respondText("Missing id", status = io.ktor.http.HttpStatusCode.BadRequest)
                    return@get
                }
                val item = repo.get(java.util.UUID.fromString(id))
                if (item == null) {
                    call.respondText("Not found", status = io.ktor.http.HttpStatusCode.NotFound)
                } else {
                    call.respond(item)
                }
            }
            get("/search") {
                val q = call.request.queryParameters["q"] ?: ""
                val (mode, items) = repo.searchAuto(q)
                call.respond(mapOf("mode" to mode, "items" to items))
            }
        }
    }
}
