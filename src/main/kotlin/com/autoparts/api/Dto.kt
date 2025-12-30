package com.autoparts.api

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val description: String,
    val partNumber: String,
    val oemNumber: String,
    val priceCents: Int,
    val isActive: Boolean
)

@Serializable
data class CreateProductRequest(
    val name: String,
    val description: String = "",
    val partNumber: String,
    val oemNumber: String = "",
    val priceCents: Int,
    val isActive: Boolean = true,
    val crossRefs: List<String> = emptyList() // просто список номеров-аналоги
)

@Serializable
data class SearchResponseDto(
    val mode: String,
    val items: List<ProductDto>
)

@Serializable
data class OrderListItemDto(
    val id: String,
    val createdAt: String,
    val status: String,
    val customerName: String? = null,
    val customerPhone: String? = null
)

@Serializable
data class OrderItemDto(
    val productId: String,
    val name: String,
    val qty: Int,
    val priceCents: Int
)

@Serializable
data class OrderDetailsDto(
    val id: String,
    val createdAt: String,
    val status: String,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val customerComment: String? = null,
    val items: List<OrderItemDto>
)