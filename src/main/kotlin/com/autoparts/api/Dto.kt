package com.autoparts.api

import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val description: String,
    val partNumber: String,
    val oemNumber: String,
    val priceCents: Int,
    val isActive: Boolean,
)

@Serializable
data class CreateProductRequest(
    val name: String,
    val description: String = "",
    val partNumber: String,
    val oemNumber: String = "",
    val priceCents: Int,
    val isActive: Boolean = true,
    val crossRefs: List<String> = emptyList(),
)

@Serializable
data class SearchResponseDto(
    val mode: String,
    val items: List<ProductDto>,
)

