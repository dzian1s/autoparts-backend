package com.autoparts.repo

import com.autoparts.api.CreateProductRequest
import com.autoparts.api.ProductDto
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

    /**
     * Улучшенный поиск:
     * 1) exact по part/oem/crossrefs
     * 2) full-text (tsvector)
     * 3) fuzzy trigram
     */
    suspend fun searchAuto(qRaw: String, limit: Int = 20): Pair<String, List<ProductDto>> = dbQuery {
        val q = qRaw.trim()
        if (q.isEmpty()) return@dbQuery "empty" to emptyList()

        // --- 1) EXACT (без deprecated slice/select) ---
        val exactFromProducts: List<UUID> =
            Products
                .select(Products.id)
                .where { (Products.partNumber eq q) or (Products.oemNumber eq q) }
                .map { it[Products.id].value }

        val exactFromCrossRefs: List<UUID> =
            ProductCrossRefs
                .select(ProductCrossRefs.productId)
                .where { ProductCrossRefs.refValue eq q }
                .map { it[ProductCrossRefs.productId] }

        val exactIds = (exactFromProducts + exactFromCrossRefs).distinct()

        if (exactIds.isNotEmpty()) {
            val items = Products
                .selectAll()
                .where { Products.id inList exactIds.map { EntityID(it, Products) } }
                .limit(limit)
                .map { it.toDto() }

            return@dbQuery "exact" to items
        }

        // --- 2) FULL-TEXT ---
        val ftsSql = """
    SELECT id, name, description, part_number, oem_number, price_cents, is_active
    FROM products
    WHERE search_vector @@ websearch_to_tsquery('simple', ?)
    ORDER BY ts_rank(search_vector, websearch_to_tsquery('simple', ?)) DESC
    LIMIT ?
""".trimIndent()

        val fts = queryProducts(ftsSql) { ps ->
            ps.setString(1, q)
            ps.setString(2, q)
            ps.setInt(3, limit)
        }

        if (fts.isNotEmpty()) return@dbQuery "fts" to fts

// --- 3) FUZZY (trigram) ---
        val fuzzySql = """
    SELECT id, name, description, part_number, oem_number, price_cents, is_active
    FROM products
    WHERE (similarity(name, ?) > 0.2) OR (similarity(part_number, ?) > 0.2)
    ORDER BY GREATEST(similarity(name, ?), similarity(part_number, ?)) DESC
    LIMIT ?
""".trimIndent()

        val fuzzy = queryProducts(fuzzySql) { ps ->
            ps.setString(1, q)
            ps.setString(2, q)
            ps.setString(3, q)
            ps.setString(4, q)
            ps.setInt(5, limit)
        }

        "fuzzy" to fuzzy

    }

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