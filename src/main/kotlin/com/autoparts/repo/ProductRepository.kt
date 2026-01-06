package com.autoparts.repo

import com.autoparts.api.CreateProductRequest
import com.autoparts.api.ProductDto
import com.autoparts.api.SearchResponseDto
import com.autoparts.db.ProductCrossRefs
import com.autoparts.db.Products
import com.autoparts.db.dbQuery
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import java.util.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ProductRepository {

    private fun normPart(s: String): String =
        s.uppercase()
            .replace(Regex("[^A-Z0-9]"), "")

    suspend fun list(limit: Int, offset: Long): List<ProductDto> = dbQuery {
        Products
            .selectAll()
            .orderBy(Products.name to SortOrder.ASC)
            .limit(limit)
            .offset(offset.coerceAtLeast(0))
            .map { it.toDto() }
    }

    suspend fun get(id: UUID): ProductDto? = dbQuery {
        Products
            .selectAll()
            .where { Products.id eq EntityID(id, Products) }
            .limit(1)
            .firstOrNull()
            ?.toDto()
    }

    suspend fun create(req: CreateProductRequest): ProductDto = dbQuery {
        val id = UUID.randomUUID()
        val eid = EntityID(id, Products)

        Products.insert {
            it[Products.id] = eid
            it[name] = req.name
            it[description] = req.description
            it[partNumber] = req.partNumber
            it[oemNumber] = req.oemNumber
            it[priceCents] = req.priceCents
            it[isActive] = req.isActive
            it[brandId] = null
            it[categoryId] = null
        }

        req.crossRefs.distinct().forEach { ref ->
            ProductCrossRefs.insert {
                it[ProductCrossRefs.id] = UUID.randomUUID()
                it[productId] = id
                it[refType] = "CROSS"
                it[refValue] = ref
            }
        }

        Products
            .selectAll()
            .where { Products.id eq eid }
            .limit(1)
            .first()
            .toDto()
    }

    suspend fun getCrossRefs(productId: UUID): List<String> = dbQuery {
        ProductCrossRefs
            .selectAll()
            .where { ProductCrossRefs.productId eq productId }
            .orderBy(ProductCrossRefs.refValue to SortOrder.ASC)
            .map { it[ProductCrossRefs.refValue] }
    }

    suspend fun update(productId: UUID, req: CreateProductRequest): ProductDto = dbQuery {
        val eid = EntityID(productId, Products)

        Products.update({ Products.id eq eid }) {
            it[name] = req.name
            it[description] = req.description
            it[partNumber] = req.partNumber
            it[oemNumber] = req.oemNumber
            it[priceCents] = req.priceCents
            it[isActive] = req.isActive
        }

        ProductCrossRefs.deleteWhere { ProductCrossRefs.productId eq productId }

        req.crossRefs.distinct().forEach { ref ->
            ProductCrossRefs.insert {
                it[id] = UUID.randomUUID()
                it[ProductCrossRefs.productId] = productId
                it[refType] = "CROSS"
                it[refValue] = ref
            }
        }

        Products
            .selectAll()
            .where { Products.id eq eid }
            .limit(1)
            .first()
            .toDto()
    }

    suspend fun searchAuto(qRaw: String, limit: Int = 20): SearchResponseDto = dbQuery {

        val q = qRaw.trim()
        if (q.isEmpty()) return@dbQuery SearchResponseDto("empty", emptyList())

        val qNorm = normPart(q)
        val hasSpace = q.any { it.isWhitespace() }
        val hasDigit = qNorm.any { it.isDigit() }

        val looksLikePart = qNorm.length >= 3 && !hasSpace && hasDigit

        // ---------------- 1) EXACT ----------------
        // 1) STRICT exact (==)
        val strictIds: List<EntityID<UUID>> =
            (Products
                .select(Products.id)
                .where {
                    (Products.partNumberNorm eq qNorm) or
                            (Products.oemNumberNorm eq qNorm)
                }
                .map { it[Products.id] } +

                    ProductCrossRefs
                        .select(ProductCrossRefs.productId)
                        .where { ProductCrossRefs.refValueNorm eq qNorm }
                        .map { EntityID(it[ProductCrossRefs.productId], Products) }
                    ).distinct()

        // 2) PREFIX (LIKE)
        val prefixIds: List<EntityID<UUID>> =
            if (!looksLikePart) emptyList()
            else Products
                .select(Products.id)
                .where {
                    (Products.partNumberNorm like "$qNorm%") or
                            (Products.oemNumberNorm like "$qNorm%")
                }
                .map { it[Products.id] }
                .distinct()

        val crossPrefixIds: List<EntityID<UUID>> =
            if (!looksLikePart) emptyList()
            else ProductCrossRefs
                .select(ProductCrossRefs.productId)
                .where { ProductCrossRefs.refValueNorm like "$qNorm%" }
                .map { EntityID(it[ProductCrossRefs.productId], Products) }
                .distinct()

        val exactIds = (strictIds + prefixIds + crossPrefixIds).distinct()

        val sortedExact =
            if (exactIds.isEmpty()) emptyList()
            else {
                val items = Products
                    .selectAll()
                    .where { Products.id inList exactIds }
                    .map { it.toDto() }

                val rank: (ProductDto) -> Int = { p ->
                    val pn = normPart(p.partNumber)
                    val on = normPart(p.oemNumber)
                    when {
                        pn == qNorm -> 0
                        on == qNorm -> 1
                        pn.startsWith(qNorm) -> 2
                        on.startsWith(qNorm) -> 3
                        else -> 4
                    }
                }

                items.sortedWith(compareBy(rank).thenBy { it.name }).take(limit)
            }

        if (sortedExact.isNotEmpty()) {//!exact —> FTS/fuzzy
            return@dbQuery SearchResponseDto("exact", sortedExact)
        }

        // ---------------- 2) FULL-TEXT ----------------
        // FTS good for words, numbers and short strings - bad
        val skipFts = q.length < 3 || looksLikePart
        if (!skipFts) {
            fun ftsMatch(qText: String): Op<Boolean> = object : Op<Boolean>() {
                override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                    queryBuilder.append("search_vector @@ websearch_to_tsquery('simple', ")
                    queryBuilder.append(stringParam(qText))
                    queryBuilder.append(")")
                }
            }

            fun ftsRank(qText: String): Expression<Double> = object : Expression<Double>() {
                override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                    queryBuilder.append("ts_rank(search_vector, websearch_to_tsquery('simple', ")
                    queryBuilder.append(stringParam(qText))
                    queryBuilder.append("))")
                }
            }

            val ftsItems = Products
                .selectAll()
                .where { ftsMatch(q) }
                .orderBy(ftsRank(q) to SortOrder.DESC, Products.name to SortOrder.ASC)
                .limit(limit)
                .map { it.toDto() }

            if (ftsItems.isNotEmpty()) return@dbQuery SearchResponseDto("fts", ftsItems)
        }

        // ---------------- 3) FUZZY (pg_trgm) ----------------
        if (q.length < 3) return@dbQuery SearchResponseDto("fuzzy", emptyList())

        val qLower = q.lowercase()

        val thrNorm = when {
            qNorm.length <= 3 -> 0.22
            qNorm.length <= 4 -> 0.28
            qNorm.length <= 6 -> 0.32
            else -> 0.35
        }

        val thrName = when {
            q.length <= 3 -> 0.20
            q.length <= 4 -> 0.24
            q.length <= 6 -> 0.28
            else -> 0.30
        }

        fun fuzzyWhere(qNormText: String): Op<Boolean> = object : Op<Boolean>() {
            override fun toQueryBuilder(qb: QueryBuilder) {
                qb.append("(")

                if (looksLikePart) {
                    // codes
                    qb.append("part_number_norm LIKE "); qb.append(stringParam("%$qNormText%"))
                    qb.append(" OR oem_number_norm LIKE "); qb.append(stringParam("%$qNormText%"))

                    // similarity for long strings
                    if (qNormText.length >= 6) {
                        qb.append(" OR similarity(part_number_norm, "); qb.append(stringParam(qNormText)); qb.append(") > $thrNorm")
                        qb.append(" OR similarity(oem_number_norm, "); qb.append(stringParam(qNormText)); qb.append(") > $thrNorm")
                    }
                } else {
                    // text
                    qb.append("word_similarity("); qb.append(stringParam(qLower)); qb.append(", lower(name)) > $thrName")
                    // text ~ code
                    qb.append(" OR similarity(part_number_norm, "); qb.append(stringParam(qNormText)); qb.append(") > $thrNorm")
                    qb.append(" OR similarity(oem_number_norm, "); qb.append(stringParam(qNormText)); qb.append(") > $thrNorm")
                }

                qb.append(")")
            }
        }

        fun fuzzyScore(qNormText: String): Expression<Double> = object : Expression<Double>() {
            override fun toQueryBuilder(qb: QueryBuilder) {

                qb.append("GREATEST(")

                if (!looksLikePart) {
                    qb.append("word_similarity("); qb.append(stringParam(qLower)); qb.append(", lower(name)), ")
                } else {
                    qb.append("0, ")
                }

                qb.append("similarity(part_number_norm, "); qb.append(stringParam(qNormText)); qb.append("), ")
                qb.append("similarity(oem_number_norm, "); qb.append(stringParam(qNormText)); qb.append(")")

                qb.append(")")
            }
        }

        val fuzzyItems = Products
            .selectAll()
            .where { fuzzyWhere(qNorm) }
            .orderBy(fuzzyScore(qNorm) to SortOrder.DESC, Products.name to SortOrder.ASC)
            .limit(limit)
            .map { it.toDto() }

        return@dbQuery SearchResponseDto("fuzzy", fuzzyItems)
    }

    private fun stringParam(value: String) = QueryParameter(value, TextColumnType())

    private fun ResultRow.toDto(): ProductDto = ProductDto(
        id = this[Products.id].value.toString(),
        name = this[Products.name],
        description = this[Products.description],
        partNumber = this[Products.partNumber],
        oemNumber = this[Products.oemNumber],
        priceCents = this[Products.priceCents],
        isActive = this[Products.isActive]
    )

}