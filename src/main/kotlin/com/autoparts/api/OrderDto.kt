package com.autoparts.api

import kotlinx.serialization.Serializable

@Serializable
data class CreateOrderItemDto(val productId: String, val qty: Int)

@Serializable
data class CreateOrderRequest(
    val clientId: String?,
    val customerName: String,
    val customerPhone: String,
    val customerComment: String? = null,
    val items: List<CreateOrderItemDto>
)

@Serializable
data class CreateOrderResponse(val orderId: String)

@Serializable
data class OrderListItemDto(
    val id: String,
    val createdAt: String,
    val status: String,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val itemsCount: Int = 0,
    val totalCents: Int = 0
)

@Serializable
data class OrderItemDto(
    val productId: String,
    val name: String,
    val qty: Int,
    val priceCents: Int,
)

@Serializable
data class OrderDetailsDto(
    val id: String,
    val createdAt: String,
    val status: String,
    val customerName: String,
    val customerPhone: String,
    val customerComment: String? = null,
    val items: List<OrderItemDto>,
)