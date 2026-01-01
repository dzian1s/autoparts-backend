package com.autoparts.routes

import com.autoparts.repo.OrderRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.adminOrdersRoutes(orderRepo: OrderRepository) {

    get("/admin/orders") {
        val orders = orderRepo.list(limit = 200, offset = 0)
        call.respondText(buildOrdersListHtml(orders), ContentType.Text.Html)
    }

    get("/admin/orders/{id}") {
        val idStr = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val uuid = runCatching { UUID.fromString(idStr) }.getOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)

        val order = orderRepo.get(uuid) ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respondText(buildOrderDetailsHtml(order), ContentType.Text.Html)
    }

    post("/admin/orders/{id}/status") {
        val idStr = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
        val uuid = runCatching { UUID.fromString(idStr) }.getOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)

        val params = call.receiveParameters()
        val status = params["status"] ?: "NEW"

        orderRepo.updateStatus(uuid, status)
        call.respondRedirect("/admin/orders/$uuid")
    }
}

private fun buildOrdersListHtml(orders: List<com.autoparts.api.OrderListItemDto>): String = """
<!doctype html>
<html>
<head>
  <meta charset="utf-8"/>
  <link rel="stylesheet" href="/static/admin.css"/>
  <title>Orders</title>
</head>
<body>
<div class="container">
  <h1>Orders</h1>
  <table class="table">
    <thead><tr><th>Created</th><th>Status</th><th>Customer</th><th>Phone</th><th></th></tr></thead>
    <tbody>
      ${orders.joinToString("") { o ->
    """<tr>
          <td>${o.createdAt}</td>
          <td><span class="badge">${o.status}</span></td>
          <td>${o.customerName ?: ""}</td>
          <td>${o.customerPhone ?: ""}</td>
          <td><a class="btn" href="/admin/orders/${o.id}">Open</a></td>
        </tr>"""
}}
    </tbody>
  </table>
  <a class="btn" href="/admin/products">Products</a>
</div>
</body>
</html>
""".trimIndent()

private fun buildOrderDetailsHtml(o: com.autoparts.api.OrderDetailsDto): String {
    val total = o.items.sumOf { it.qty * it.priceCents }
    return """
<!doctype html>
<html>
<head>
  <meta charset="utf-8"/>
  <link rel="stylesheet" href="/static/admin.css"/>
  <title>Order ${o.id}</title>
</head>
<body>
<div class="container">
  <a class="btn" href="/admin/orders">← Back</a>
  <h1>Order</h1>
  <div class="card">
    <div><b>ID:</b> ${o.id}</div>
    <div><b>Created:</b> ${o.createdAt}</div>
    <div><b>Status:</b> <span class="badge">${o.status}</span></div>
    <div><b>Name:</b> ${o.customerName ?: ""}</div>
    <div><b>Phone:</b> ${o.customerPhone ?: ""}</div>
    <div><b>Comment:</b> ${o.customerComment ?: ""}</div>
  </div>

  <h2>Items</h2>
  <table class="table">
    <thead><tr><th>Product</th><th>Qty</th><th>Price</th><th>Sum</th></tr></thead>
    <tbody>
      ${o.items.joinToString("") { itx ->
        val sum = itx.qty * itx.priceCents
        """<tr>
          <td>${itx.name}</td>
          <td>${itx.qty}</td>
          <td>${itx.priceCents}</td>
          <td>$sum</td>
        </tr>"""
    }}
    </tbody>
  </table>

  <div class="card"><b>Total:</b> $total</div>

  <h2>Status</h2>
  <form method="post" action="/admin/orders/${o.id}/status">
    <button class="btn" name="status" value="NEW">NEW</button>
    <button class="btn" name="status" value="IN_PROGRESS">IN_PROGRESS</button>
    <button class="btn" name="status" value="DONE">DONE</button>
  </form>
</div>
</body>
</html>
""".trimIndent()
}
