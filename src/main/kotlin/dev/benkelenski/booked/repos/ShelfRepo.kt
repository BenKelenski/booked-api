package dev.benkelenski.booked.repos

import dev.benkelenski.booked.models.Shelf
import dev.benkelenski.booked.models.ShelfTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.transaction

class ShelfRepo {

    fun getAllShelves(): List<Shelf> = transaction {
        addLogger(StdOutSqlLogger)
        ShelfTable.selectAll()
            .map(::mapToShelf)
    }

    fun getShelfById(id: Int): Shelf? = transaction {
        addLogger(StdOutSqlLogger)
        ShelfTable.selectAll()
            .where { ShelfTable.id eq id }
            .map(::mapToShelf)
            .singleOrNull()
    }

    fun addShelf(name: String, description: String?): Shelf? = transaction {
        addLogger(StdOutSqlLogger)
        ShelfTable.insertReturning {
            it[ShelfTable.name]
            it[ShelfTable.description] = description
            it[ShelfTable.createdAt] = CurrentTimestampWithTimeZone
        }
            .map(::mapToShelf)
            .singleOrNull()
    }

    fun deleteShelf(id: Int): Int = transaction {
        addLogger(StdOutSqlLogger)
        ShelfTable.deleteWhere {
            ShelfTable.id eq id
        }
    }

    private fun mapToShelf(row: ResultRow): Shelf = Shelf(
        id = row[ShelfTable.id],
        name = row[ShelfTable.name],
        description = row[ShelfTable.description],
        createdAt = row[ShelfTable.createdAt].toInstant()
    )
}