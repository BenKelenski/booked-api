package dev.benkelenski.booked.repos

import dev.benkelenski.booked.domain.ReadingStatus
import dev.benkelenski.booked.domain.Shelf
import dev.benkelenski.booked.models.Shelves
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ShelfRepo {

    companion object {
        private val DEFAULT_SHELVES =
            listOf(
                Triple("To Read", ReadingStatus.TO_READ, false),
                Triple("Reading", ReadingStatus.READING, false),
                Triple("Finished", ReadingStatus.FINISHED, false),
            )
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

    fun createDefaultShelves(userId: Int): List<Shelf> =
        Shelves.batchInsert(DEFAULT_SHELVES) { (name, status, isDeletable) ->
                this[Shelves.userId] = userId
                this[Shelves.name] = name
                this[Shelves.isDeletable] = isDeletable
                this[Shelves.readingStatus] = status
            }
            .map { it.toShelf() }

    fun deleteByIdAndUser(userId: Int, shelfId: Int): Int? {
        val shelf =
            Shelves.selectAll()
                .where { (Shelves.id eq shelfId) and (Shelves.userId eq userId) }
                .map { it.toShelf() }
                .singleOrNull() ?: return null

        if (!shelf.isDeletable) return null

        return Shelves.deleteWhere { (Shelves.id eq shelfId) and (Shelves.userId eq userId) }
    }

    fun existsById(shelfId: Int): Boolean =
        Shelves.selectAll().where { Shelves.id eq shelfId }.limit(1).any()

    fun userOwnsShelf(userId: Int, shelfId: Int): Boolean =
        Shelves.selectAll()
            .where { (Shelves.id eq shelfId) and (Shelves.userId eq userId) }
            .limit(1)
            .any()

    fun findShelfByStatus(userId: Int, status: ReadingStatus): Shelf? =
        Shelves.selectAll()
            .where { (Shelves.userId eq userId) and (Shelves.readingStatus eq status) }
            .map { it.toShelf() }
            .singleOrNull()
}

fun ResultRow.toShelf() =
    Shelf(
        id = this[Shelves.id],
        userId = this[Shelves.userId],
        name = this[Shelves.name],
        description = this[Shelves.description],
        isDeletable = this[Shelves.isDeletable],
        readingStatus = this[Shelves.readingStatus],
        createdAt = this[Shelves.createdAt].toInstant(),
    )
