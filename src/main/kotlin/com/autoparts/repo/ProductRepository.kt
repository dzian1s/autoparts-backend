package com.autoparts.repo

import com.autoparts.api.CreateProductRequest
import com.autoparts.api.ProductDto
import com.autoparts.api.SearchResponseDto
import com.autoparts.db.ProductCrossRefs
import com.autoparts.db.Products
import com.autoparts.db.dbQuery
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.PreparedStatement
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
            .limit(limit)          // новый DSL
            .offset(offset.coerceAtLeast(0))        // новый DSL
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
            it[Products.id] = eid               // ВАЖНО: UUIDTable -> EntityID
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
                it[productId] = id              // productId в таблице у тебя uuid(), так что UUID ок
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

        // пересохраняем кроссы: удаляем старые, пишем новые
        ProductCrossRefs.deleteWhere { ProductCrossRefs.productId eq productId }

        req.crossRefs.distinct().forEach { ref ->
            ProductCrossRefs.insert {
                it[ProductCrossRefs.id] = UUID.randomUUID()
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

    private fun pid(uuid: UUID) = EntityID(uuid, Products)

    suspend fun searchAuto(qRaw: String, limit: Int = 20): SearchResponseDto = dbQuery {

        val q = qRaw.trim()
        if (q.isEmpty()) return@dbQuery SearchResponseDto("empty", emptyList())

        val qNorm = normPart(q)
        val looksLikePart = qNorm.length >= 4

        val exactIds: List<EntityID<UUID>> = (
                Products
                    .select(Products.id)
                    .where {
                        val prefixOp: Op<Boolean> =
                            if (looksLikePart) (Products.partNumberNorm like "$qNorm%") else Op.FALSE

                        (Products.partNumberNorm eq qNorm) or
                                (Products.oemNumberNorm eq qNorm) or
                                prefixOp
                    }
                    .map { it[Products.id] } // <-- EntityID<UUID>
                        +
                        ProductCrossRefs
                            .select(ProductCrossRefs.productId)
                            .where { ProductCrossRefs.refValueNorm eq qNorm }
                            .map { it[ProductCrossRefs.productId] } // <-- EntityID<UUID>
                ).distinct() as List<EntityID<UUID>>

        if (exactIds.isNotEmpty()) {
            val items = Products
                .selectAll()
                .where { Products.id inList exactIds }
                .limit(limit)
                .map { it.toDto() }

            return@dbQuery SearchResponseDto("exact", items)
        }

        // ---------------- 2) FULL-TEXT ----------------
        // FTS имеет смысл для "слов", но хуже для артикулов и коротких строк
        val skipFts = looksLikePart || q.length < 3
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

        // чем короче строка — тем выше порог, иначе будет мусор
        val thr = when (q.length) {
            3 -> 0.35
            4 -> 0.30
            5, 6 -> 0.25
            else -> 0.20
        }

        fun fuzzyWhere(qText: String): Op<Boolean> = object : Op<Boolean>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append("(")
                queryBuilder.append("similarity(name, "); queryBuilder.append(stringParam(qText)); queryBuilder.append(") > $thr")
                queryBuilder.append(" OR similarity(part_number_norm, "); queryBuilder.append(stringParam(qText)); queryBuilder.append(") > $thr")
                queryBuilder.append(" OR similarity(oem_number_norm, "); queryBuilder.append(stringParam(qText)); queryBuilder.append(") > $thr")
                queryBuilder.append(")")
            }
        }

        fun fuzzyScore(qText: String): Expression<Double> = object : Expression<Double>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append("GREATEST(")
                queryBuilder.append("similarity(name, "); queryBuilder.append(stringParam(qText)); queryBuilder.append("), ")
                queryBuilder.append("similarity(part_number_norm, "); queryBuilder.append(stringParam(qText)); queryBuilder.append("), ")
                queryBuilder.append("similarity(oem_number_norm, "); queryBuilder.append(stringParam(qText)); queryBuilder.append(")")
                queryBuilder.append(")")
            }
        }

        val fuzzyItems = Products
            .selectAll()
            .where { fuzzyWhere(q) }
            .orderBy(fuzzyScore(q) to SortOrder.DESC, Products.name to SortOrder.ASC)
            .limit(limit)
            .map { it.toDto() }

        SearchResponseDto("fuzzy", fuzzyItems)
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

    private fun queryProducts(sql: String, bind: (PreparedStatement) -> Unit): List<ProductDto> {
        val tx = TransactionManager.current()

        val jdbc = (tx.connection.connection as? java.sql.Connection)
            ?: error("Expected JDBC Connection, got: ${tx.connection.connection::class.qualifiedName}")

        val ps = jdbc.prepareStatement(sql)
        try {
            bind(ps)
            val rs = ps.executeQuery()
            try {
                val out = mutableListOf<ProductDto>()
                while (rs.next()) {
                    out += ProductDto(
                        id = rs.getObject("id").toString(),
                        name = rs.getString("name"),
                        description = rs.getString("description"),
                        partNumber = rs.getString("part_number"),
                        oemNumber = rs.getString("oem_number"),
                        priceCents = rs.getInt("price_cents"),
                        isActive = rs.getBoolean("is_active"),
                    )
                }
                return out
            } finally {
                rs.close()
            }
        } finally {
            ps.close()
        }
    }
}