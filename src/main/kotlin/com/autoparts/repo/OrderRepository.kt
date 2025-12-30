package com.autoparts.repo

import CreateOrderRequest
import com.autoparts.api.OrderDetailsDto
import com.autoparts.api.OrderItemDto
import com.autoparts.api.OrderListItemDto
import com.autoparts.db.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import java.util.UUID

class OrderRepository {

        suspend fun list(limit: Int = 50, offset: Long = 0): List<OrderListItemDto> = dbQuery {
            Orders
                .selectAll()
                .orderBy(Orders.createdAt to SortOrder.DESC)
                .limit(limit, offset = offset)
                .map { row ->
                    OrderListItemDto(
                        id = row[Orders.id].value.toString(),
                        createdAt = row[Orders.createdAt].toString(),
                        status = row[Orders.status],
                        customerName = row[Orders.customerName],
                        customerPhone = row[Orders.customerPhone]
                    )
                }
        }

        suspend fun get(id: UUID): OrderDetailsDto? = dbQuery {
            val oid = EntityID(id, Orders)

            val head = Orders
                .selectAll()
                .where { Orders.id eq oid }
                .limit(1)
                .firstOrNull() ?: return@dbQuery null

            val items = (OrderItems innerJoin Products)
                .select(
                    OrderItems.qty,
                    OrderItems.priceCents,
                    Products.id,
                    Products.name
                )
                .where { OrderItems.order eq oid }
                .map { row ->
                    OrderItemDto(
                        productId = row[Products.id].value.toString(),
                        name = row[Products.name],
                        qty = row[OrderItems.qty],
                        priceCents = row[OrderItems.priceCents]
                    )
                }

            OrderDetailsDto(
                id = head[Orders.id].value.toString(),
                createdAt = head[Orders.createdAt].toString(),
                status = head[Orders.status],
                customerName = head[Orders.customerName],
                customerPhone = head[Orders.customerPhone],
                customerComment = head[Orders.customerComment],
                items = items
            )
        }


        suspend fun create(req: CreateOrderRequest): UUID = dbQuery {
        require(req.items.isNotEmpty()) { "Order items must not be empty" }

        // 1) нормализуем items
        val normalized: List<Pair<UUID, Int>> = req.items.map { itx ->
            val pid = runCatching { UUID.fromString(itx.productId) }.getOrNull()
                ?: throw IllegalArgumentException("Bad productId: ${itx.productId}")
            if (itx.qty <= 0) throw IllegalArgumentException("qty must be > 0 for $pid")
            pid to itx.qty
        }

        // 2) цены из БД (key = UUID)
        val productIds = normalized.map { it.first }.distinct()
        val productEntityIds = productIds.map { EntityID(it, Products) }

        val productsMap: Map<UUID, Int> =
            Products
                .select(Products.id, Products.priceCents)
                .where { Products.id inList productEntityIds }
                .associate { row ->
                    row[Products.id].value to row[Products.priceCents]
                }

        if (productsMap.size != productIds.size) {
            val missing = productIds.filter { it !in productsMap.keys }
            throw IllegalArgumentException("Some products not found: $missing")
        }

        // 3) orders
        val newOrderId = UUID.randomUUID()

        Orders.insert {
            it[Orders.id] = EntityID(newOrderId, Orders)
            // created_at пусть ставит DEFAULT now()
            it[Orders.status] = "NEW"
            it[Orders.customerName] = req.customerName
            it[Orders.customerPhone] = req.customerPhone
            it[Orders.customerComment] = req.customerComment
        }

        // 4) order_items (orderId/productId через reference => EntityID)
        normalized.forEach { (pid, qty) ->
            OrderItems.insert {
                it[OrderItems.id] = UUID.randomUUID()
                it[OrderItems.order] = EntityID(newOrderId, Orders)
                it[OrderItems.product] = EntityID(pid, Products)
                it[OrderItems.qty] = qty
                it[OrderItems.priceCents] = productsMap.getValue(pid)
            }
        }
            newOrderId
    }
    suspend fun updateStatus(id: UUID, status: String): Boolean = dbQuery {
        val oid = EntityID(id, Orders)
        Orders.update({ Orders.id eq oid }) {
            it[Orders.status] = status
        } > 0
    }

}
