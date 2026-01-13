package dev.benkelenski.booked.repos

import dev.benkelenski.booked.domain.Shelf
import dev.benkelenski.booked.domain.ShelfType
import dev.benkelenski.booked.models.Books
import dev.benkelenski.booked.models.Shelves
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class ShelfRepo {

    companion object {
        private val DEFAULT_SHELVES =
            listOf(
                Pair("To Read", ShelfType.TO_READ),
                Pair("Reading", ShelfType.READING),
                Pair("Finished", ShelfType.FINISHED),
            )
    }

    fun addShelf(userId: Int, name: String, description: String?): Shelf? =
        Shelves.insertReturning {
                it[Shelves.userId] = userId
                it[Shelves.name] = name
                it[Shelves.description] = description
            }
            .map { it.toShelf() }
            .singleOrNull()

    fun createDefaultShelves(userId: Int): List<Shelf> =
        Shelves.batchInsert(DEFAULT_SHELVES) { (name, shelfType) ->
                this[Shelves.userId] = userId
                this[Shelves.name] = name
                this[Shelves.shelfType] = shelfType
            }
            .map { it.toShelf() }

    fun deleteByIdAndUser(userId: Int, shelfId: Int): Int? {
        val shelf =
            Shelves.selectAll()
                .where { (Shelves.id eq shelfId) and (Shelves.userId eq userId) }
                .map { it.toShelf() }
                .singleOrNull() ?: return null

        if (shelf.shelfType != ShelfType.CUSTOM) return null

        return Shelves.deleteWhere { (Shelves.id eq shelfId) and (Shelves.userId eq userId) }
    }

    fun existsById(shelfId: Int): Boolean =
        Shelves.selectAll().where { Shelves.id eq shelfId }.limit(1).any()

    fun findShelfByType(userId: Int, type: ShelfType): Shelf? =
        Shelves.selectAll()
            .where { (Shelves.userId eq userId) and (Shelves.shelfType eq type) }
            .map { it.toShelf() }
            .singleOrNull()

    fun fetchShelvesWithBookCounts(userId: Int, shelfId: Int? = null): List<Shelf> {
        val query =
            Shelves.join(
                    otherTable = Books,
                    joinType = JoinType.LEFT,
                    onColumn = Shelves.id,
                    otherColumn = Books.shelfId,
                )
                .select(
                    Shelves.id,
                    Shelves.userId,
                    Shelves.name,
                    Shelves.description,
                    Shelves.shelfType,
                    Shelves.createdAt,
                    Books.id.count(),
                )
                .where { Shelves.userId eq userId }

        shelfId?.let { query.andWhere { Shelves.id eq it } }

        return query.groupBy(Shelves.id).map { it.toShelfWithCount() }
    }
}

fun ResultRow.toShelf() =
    Shelf(
        id = this[Shelves.id],
        userId = this[Shelves.userId],
        name = this[Shelves.name],
        description = this[Shelves.description],
        shelfType = this[Shelves.shelfType],
        bookCount = 0, // Default value when count not queried
        createdAt = this[Shelves.createdAt].toInstant(),
    )

fun ResultRow.toShelfWithCount() =
    Shelf(
        id = this[Shelves.id],
        userId = this[Shelves.userId],
        name = this[Shelves.name],
        description = this[Shelves.description],
        shelfType = this[Shelves.shelfType],
        bookCount = this[Books.id.count()],
        createdAt = this[Shelves.createdAt].toInstant(),
    )
