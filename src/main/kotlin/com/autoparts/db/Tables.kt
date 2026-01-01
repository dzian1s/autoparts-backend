package com.autoparts.db

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object Brands : UUIDTable("brands") {
    val name = text("name").uniqueIndex()
}

object Categories : UUIDTable("categories") {
    val name = text("name")
    val parentId = uuid("parent_id").nullable()
}

object Products : UUIDTable("products") {
    val name = text("name")
    val description = text("description")
    val brandId = uuid("brand_id").nullable()
    val categoryId = uuid("category_id").nullable()
    val partNumber = text("part_number")
    val oemNumber = text("oem_number")
    val priceCents = integer("price_cents")
    val isActive = bool("is_active")
    val partNumberNorm = text("part_number_norm")
    val oemNumberNorm = text("oem_number_norm")

}

object ProductCrossRefs : UUIDTable("product_cross_refs") {
    val productId = uuid("product_id").references(Products.id, onDelete = ReferenceOption.CASCADE)
    val refType = text("ref_type")
    val refValue = text("ref_value")
    val refValueNorm = text("ref_value_norm")

}
