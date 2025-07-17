package dev.benkelenski.booked.repos

import dev.benkelenski.booked.domain.Shelf
import dev.benkelenski.booked.models.Shelves
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ShelfRepo {

    fun getAllShelves(): List<Shelf> = transaction { Shelves.selectAll().map { it.toShelf() } }

    fun getShelfById(id: Int): Shelf? = transaction {
        Shelves.selectAll().where { Shelves.id eq id }.map { it.toShelf() }.singleOrNull()
    }

    fun addShelf(userId: Int, name: String, description: String?): Shelf? = transaction {
        Shelves.insertReturning {
                it[Shelves.userId] = userId
                it[Shelves.name] = name
                it[Shelves.description] = description
            }
            .map { it.toShelf() }
            .singleOrNull()
    }

    fun deleteShelf(id: Int): Int = transaction { Shelves.deleteWhere { Shelves.id eq id } }
}

fun ResultRow.toShelf() =
    Shelf(
        id = this[Shelves.id],
        userId = this[Shelves.userId],
        name = this[Shelves.name],
        description = this[Shelves.description],
        createdAt = this[Shelves.createdAt].toInstant(),
    )
