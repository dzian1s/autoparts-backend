package com.autoparts.seed

import com.autoparts.db.ProductCrossRefs
import com.autoparts.db.Products
import com.autoparts.db.dbQueryBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

object DataSeeder {

    fun seedIfEmpty() = dbQueryBlocking {
        val existing = Products.selectAll().count()
        if (existing > 0) return@dbQueryBlocking

        val samples = listOf(
            Sample("Bosch Oil Filter", "P7079", "0986AF0709", 2500, listOf("OF-7079", "P-7079")),
            Sample("Mann Filter Air Filter", "C27009", "MANN-C27009", 3200, listOf("AF-C27009")),
            Sample("NGK Spark Plug", "BKR6E", "NGK-BKR6E", 1800, listOf("SP-BKR6E", "2460")),
            Sample("Brembo Brake Pad Set", "P 85 020", "BREMBO-P85020", 8900, listOf("BP-P85020")),
            Sample("SKF Wheel Bearing", "VKBA 3643", "SKF-VKBA3643", 15900, listOf("WB-3643")),
        )

        // размножим до 25 товаров
        val expanded = buildList {
            var i = 1
            while (size < 25) {
                samples.forEach { s ->
                    if (size >= 25) return@forEach
                    add(
                        s.copy(
                            name = "${s.name} #$i",
                            partNumber = normalizePart("${s.partNumber}-$i"),
                            crossRefs = s.crossRefs.map { normalizePart("$it-$i") }
                        )
                    )
                    i++
                }
            }
        }

        expanded.forEach { s ->
            val id = UUID.randomUUID()
            val eid = EntityID(id, Products)

            Products.insert {
                it[Products.id] = eid
                it[name] = s.name
                it[description] = "Seed item for demo/search testing"
                it[partNumber] = s.partNumber
                it[oemNumber] = s.oemNumber
                it[priceCents] = s.priceCents
                it[isActive] = true
                it[brandId] = null
                it[categoryId] = null
            }

            s.crossRefs.distinct().forEach { ref ->
                ProductCrossRefs.insert {
                    it[ProductCrossRefs.id] = UUID.randomUUID()
                    it[productId] = id
                    it[refType] = "CROSS"
                    it[refValue] = ref
                }
            }
        }
    }

    private fun normalizePart(s: String): String =
        s.trim().replace(" ", "").replace("/", "-")

    private data class Sample(
        val name: String,
        val partNumber: String,
        val oemNumber: String,
        val priceCents: Int,
        val crossRefs: List<String>
    )
}
