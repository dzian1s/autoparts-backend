package com.autoparts.db

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Orders : UUIDTable("orders") {
    val createdAt = timestampWithTimeZone("created_at")
    val status = text("status")
    val customerName = text("customer_name").nullable()
    val customerPhone = text("customer_phone").nullable()
    val customerComment = text("customer_comment").nullable()
}

object OrderItems : UUIDTable("order_items") {
    val order = reference("order_id", Orders)
    val product = reference("product_id", Products)
    val qty = integer("qty")
    val priceCents = integer("price_cents")
}
