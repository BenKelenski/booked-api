package dev.benkelenski.booked.repos

import dev.benkelenski.booked.domain.Shelf
import dev.benkelenski.booked.models.Shelves
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ShelfRepo {

    companion object {
        private val DEFAULT_SHELF_NAMES = listOf("To Read", "Reading", "Finished")
    }

    fun fetchAllShelvesByUser(userId: Int): List<Shelf> =
        Shelves.selectAll().where { Shelves.userId eq userId }.map { it.toShelf() }

    fun fetchShelfById(userId: Int, shelfId: Int): Shelf? =
        Shelves.selectAll()
            .where { (Shelves.id eq shelfId) and (Shelves.userId eq userId) }
            .map { it.toShelf() }
            .singleOrNull()

    fun addShelf(userId: Int, name: String, description: String?): Shelf? =
        Shelves.insertReturning {
                it[Shelves.userId] = userId
                it[Shelves.name] = name
                it[Shelves.description] = description
            }
            .map { it.toShelf() }
            .singleOrNull()

    fun createDefaultShelves(userId: Int) =
        Shelves.batchInsert(DEFAULT_SHELF_NAMES) { name ->
            this[Shelves.userId] = userId
            this[Shelves.name] = name
            this[Shelves.isDeletable] = false
        }

    fun deleteByIdAndUser(userId: Int, shelfId: Int): Int? {
        val row =
            Shelves.select(Shelves.isDeletable)
                .where { (Shelves.id eq shelfId) and (Shelves.userId eq userId) }
                .singleOrNull() ?: return null

        if (!row[Shelves.isDeletable]) return null

        return Shelves.deleteWhere { (Shelves.id eq shelfId) and (Shelves.userId eq userId) }
    }

    fun existsById(id: Int): Boolean = Shelves.selectAll().where { Shelves.id eq id }.limit(1).any()

    fun userOwnsShelf(userId: Int, shelfId: Int): Boolean =
        Shelves.selectAll().where { (Shelves.id eq shelfId) and (Shelves.userId eq userId) }.any()
}

fun ResultRow.toShelf() =
    Shelf(
        id = this[Shelves.id],
        userId = this[Shelves.userId],
        name = this[Shelves.name],
        description = this[Shelves.description],
        isDeletable = this[Shelves.isDeletable],
        createdAt = this[Shelves.createdAt].toInstant(),
    )
