package com.autoparts.routes

import com.autoparts.api.CreateProductRequest
import com.autoparts.repo.ProductRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import io.ktor.server.html.*
import io.ktor.server.http.content.staticResources
import java.util.UUID

fun Application.registerAdminRoutes() {
    val repo = ProductRepository()

    routing {

        staticResources("/static", "static")

        route("/admin") {

            // Главная админки -> редирект на товары
            get {
                call.respondRedirect("/admin/products")
            }

            // Список товаров + быстрый поиск
            get("/products") {
                val q = call.request.queryParameters["q"]?.trim().orEmpty()
                val items = if (q.isBlank()) {
                    repo.list(limit = 50, offset = 0)
                } else {
                    repo.searchAuto(q, limit = 50).second
                }

                call.respondHtml(HttpStatusCode.OK) {
                    head {
                        title { +"Admin - Products" }
                        link(rel = "stylesheet", href = "/static/admin.css")
                    }
                    body {
                        div("container") {
                            h1 { +"Products (micro-admin)" }

                            div("row") {
                                form(action = "/admin/products", method = FormMethod.get) {
                                    input(type = InputType.text, name = "q") {
                                        placeholder = "Search (part/oem/name, typos ok)"
                                        value = q
                                    }
                                    button(type = ButtonType.submit) { +"Search" }
                                }
                                div("spacer") {}
                                a(href = "/admin/products/new", classes = "button") { +"Add product" }
                            }

                            table {
                                thead {
                                    tr {
                                        th { +"Name" }
                                        th { +"Part #" }
                                        th { +"OEM" }
                                        th { +"Price (cents)" }
                                        th { +"Active" }
                                        th { +"ID" }
                                        th { +"Edit" }
                                    }
                                }
                                tbody {
                                    items.forEach { p ->
                                        tr {
                                            td { +p.name }
                                            td { +p.partNumber }
                                            td { +p.oemNumber }
                                            td { +p.priceCents.toString() }
                                            td { +(if (p.isActive) "yes" else "no") }
                                            td { +p.id }
                                            td { a(href = "/admin/products/${p.id}/edit") { +"Edit" } }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Форма добавления
            get("/products/new") {
                call.respondHtml(HttpStatusCode.OK) {
                    head {
                        title { +"Admin - New product" }
                        link(rel = "stylesheet", href = "/static/admin.css")
                    }
                    body {
                        div ("container") {
                            h1 { +"Add product" }

                            form(action = "/admin/products/new", method = FormMethod.post) {

                                label { +"Name" }
                                input(type = InputType.text, name = "name") { required = true }

                                label { +"Description" }
                                textArea { name = "description" }

                                div("row") {
                                    div {
                                        label { +"Part number" }
                                        input(type = InputType.text, name = "partNumber") { required = true }
                                    }
                                    div {
                                        label { +"OEM number" }
                                        input(type = InputType.text, name = "oemNumber")
                                    }
                                }

                                div("row") {
                                    div {
                                        label { +"Price (cents)" }
                                        input(type = InputType.number, name = "priceCents") {
                                            required = true
                                            value = "1000"
                                        }
                                    }
                                    div {
                                        label { +"Active (true/false)" }
                                        input(type = InputType.text, name = "isActive") { value = "true" }
                                    }
                                }

                                label { +"Cross refs (one per line)" }
                                textArea { name = "crossRefs" }

                                button(type = ButtonType.submit) { +"Create" }
                            }

                            a(href = "/admin/products") { +"← Back to products" }
                        }
                    }
                }
            }

            // Обработка формы
            post("/products/new") {
                val form = call.receiveParameters()

                var name = form["name"]?.trim().orEmpty()
                val description = form["description"]?.trim().orEmpty()
                val partNumber = form["partNumber"]?.trim().orEmpty()
                val oemNumber = form["oemNumber"]?.trim().orEmpty()
                val priceCents = form["priceCents"]?.toIntOrNull() ?: 0
                val isActive = (form["isActive"]?.trim()?.lowercase() ?: "true") == "true"

                val crossRefs = (form["crossRefs"] ?: "")
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (name.isBlank() || partNumber.isBlank() || priceCents <= 0) {
                    return@post call.respondText(
                        "Validation failed: name/partNumber required, priceCents > 0",
                        status = HttpStatusCode.BadRequest
                    )
                }

                repo.create(
                    CreateProductRequest(
                        name = name,
                        description = description,
                        partNumber = partNumber,
                        oemNumber = oemNumber,
                        priceCents = priceCents,
                        isActive = isActive,
                        crossRefs = crossRefs
                    )
                )

                call.respondRedirect("/admin/products")
            }

            get("/products/{id}/edit") {
                val idStr = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
                val id = runCatching { UUID.fromString(idStr) }.getOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid UUID")

                val product = repo.get(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                val crossRefs = repo.getCrossRefs(id).joinToString("\n")

                call.respondHtml(HttpStatusCode.OK) {
                    head {
                        title { +"Admin - Edit product" }
                        link(rel = "stylesheet", href = "/static/admin.css")
                    }
                    body {
                        div ("container"){
                            h1 { +"Edit product" }

                            form(action = "/admin/products/$idStr/edit", method = FormMethod.post) {

                            label { +"Name" }
                            input(type = InputType.text, name = "name") {
                                required = true
                                value = product.name
                            }

                            label { +"Description" }
                            textArea {
                                name = "description"
                                +product.description
                            }

                            div("row") {
                                div {
                                    label { +"Part number" }
                                    input(type = InputType.text, name = "partNumber") {
                                        required = true
                                        value = product.partNumber
                                    }
                                }
                                div {
                                    label { +"OEM number" }
                                    input(type = InputType.text, name = "oemNumber") {
                                        value = product.oemNumber
                                    }
                                }
                            }

                            div("row") {
                                div {
                                    label { +"Price (cents)" }
                                    input(type = InputType.number, name = "priceCents") {
                                        required = true
                                        value = product.priceCents.toString()
                                    }
                                }
                                div {
                                    label { +"Active (true/false)" }
                                    input(type = InputType.text, name = "isActive") {
                                        value = if (product.isActive) "true" else "false"
                                    }
                                }
                            }

                            label { +"Cross refs (one per line)" }
                            textArea {
                                name = "crossRefs"
                                +crossRefs
                            }

                            button(type = ButtonType.submit) { +"Save" }
                        }

                            a(href = "/admin/products") { +"← Back to products" }
                        }
                    }
                }
            }

            post("/products/{id}/edit") {
                val idStr = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing id")
                val id = runCatching { UUID.fromString(idStr) }.getOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid UUID")

                val form = call.receiveParameters()

                val name = form["name"]?.trim().orEmpty()
                val description = form["description"]?.trim().orEmpty()
                val partNumber = form["partNumber"]?.trim().orEmpty()
                val oemNumber = form["oemNumber"]?.trim().orEmpty()
                val priceCents = form["priceCents"]?.toIntOrNull() ?: 0
                val isActive = (form["isActive"]?.trim()?.lowercase() ?: "true") == "true"

                val crossRefs = (form["crossRefs"] ?: "")
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                if (name.isBlank() || partNumber.isBlank() || priceCents <= 0) {
                    return@post call.respondText(
                        "Validation failed: name/partNumber required, priceCents > 0",
                        status = HttpStatusCode.BadRequest
                    )
                }

                repo.update(
                    productId = id,
                    req = CreateProductRequest(
                        name = name,
                        description = description,
                        partNumber = partNumber,
                        oemNumber = oemNumber,
                        priceCents = priceCents,
                        isActive = isActive,
                        crossRefs = crossRefs
                    )
                )

                call.respondRedirect("/admin/products")
            }
        }
    }
}
