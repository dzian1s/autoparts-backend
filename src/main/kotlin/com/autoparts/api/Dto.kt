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
data class SearchResponse(
    val query: String,
    val mode: String, // exact / fts / fuzzy
    val items: List<ProductDto>
)
