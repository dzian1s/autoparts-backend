package com.autoparts.routes

import CreateOrderRequest
import CreateOrderResponse
import com.autoparts.api.*
import com.autoparts.repo.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.receive
import java.util.UUID

fun Application.configureRouting() {

    val orderRepo = OrderRepository()
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
                    call.respondText("Missing id", status = HttpStatusCode.BadRequest)
                    return@get
                }
                val item = repo.get(UUID.fromString(id))
                if (item == null) {
                    call.respondText("Not found", status = HttpStatusCode.NotFound)
                } else {
                    call.respond(item)
                }
            }
            get("/search") {
                val q = call.request.queryParameters["q"] ?: ""
                val (mode, items) = repo.searchAuto(q)
                call.respond(SearchResponseDto(mode = mode, items = items))
            }
            post("/orders") {
                val req = call.receive<CreateOrderRequest>()
                val orderId = orderRepo.create(req)
                call.respond(CreateOrderResponse(orderId.toString()))
            }

            get("/orders") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0
                call.respond(orderRepo.list(limit, offset))
            }

            // детали
            get("/orders/{id}") {
                val idStr = call.parameters["id"] ?: return@get call.respondText("Missing id", status = io.ktor.http.HttpStatusCode.BadRequest)
                val uuid = runCatching { UUID.fromString(idStr) }.getOrNull()
                    ?: return@get call.respondText("Bad id", status = io.ktor.http.HttpStatusCode.BadRequest)

                val order = orderRepo.get(uuid)
                    ?: return@get call.respondText("Not found", status = io.ktor.http.HttpStatusCode.NotFound)

                call.respond(order)
            }
        }
        adminOrdersRoutes(orderRepo)
    }

}
