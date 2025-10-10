package dev.benkelenski.booked.repos

import dev.benkelenski.booked.domain.Book
import dev.benkelenski.booked.models.Books
import dev.benkelenski.booked.models.Shelves
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.time.ZoneOffset

class BookRepo {

    data class OwnedBookMinimal(val id: Int, val shelfId: Int, val googleId: String)

    fun findOwnedMinimal(bookId: Int, userId: Int): OwnedBookMinimal? =
        (Books innerJoin Shelves)
            .select(Books.id, Books.shelfId, Books.googleId)
            .where { (Books.id eq bookId) and (Shelves.userId eq userId) }
            .singleOrNull()
            ?.let {
                OwnedBookMinimal(
                    id = it[Books.id],
                    shelfId = it[Books.shelfId],
                    googleId = it[Books.googleId],
                )
            }

    fun fetchAllBooksByUser(userId: Int): List<Book> =
        Books.selectAll().where { Books.userId eq userId }.map { it.toBook() }

    fun fetchById(bookId: Int): Book? =
        Books.selectAll().where { Books.id eq bookId }.map { it.toBook() }.singleOrNull()

    fun saveBook(
        userId: Int,
        shelfId: Int,
        googleId: String,
        title: String,
        authors: List<String>,
        thumbnailUrl: String? = null,
        pageCount: Int? = null,
    ): Book? =
        Books.insertReturning {
                it[this.userId] = userId
                it[this.googleId] = googleId
                it[this.title] = title
                it[this.authors] = authors
                it[this.shelfId] = shelfId
                it[this.thumbnailUrl] = thumbnailUrl
                it[this.pageCount] = pageCount
            }
            .map { it.toBook() }
            .singleOrNull()

    fun deleteByIdAndUser(userId: Int, bookId: Int): Int =
        Books.deleteWhere { (Books.userId eq userId) and (Books.id eq bookId) }

    fun existsById(id: Int): Boolean = Books.selectAll().where { Books.id eq id }.limit(1).any()

    fun findAllByShelfAndUser(shelfId: Int, userId: Int): List<Book> =
        Books.selectAll()
            .where { (Books.userId eq userId) and (Books.shelfId eq shelfId) }
            .map { it.toBook() }

    fun existsByShelfAndGoogleId(shelfId: Int, googleId: String): Boolean =
        Books.selectAll()
            .where { (Books.shelfId eq shelfId) and (Books.googleId eq googleId) }
            .limit(1)
            .any()

    fun existsDuplicateOnShelf(
        targetShelfId: Int,
        googleId: String,
        excludingBookId: Int,
    ): Boolean =
        Books.selectAll()
            .where {
                (Books.shelfId eq targetShelfId) and
                    (Books.googleId eq googleId) and
                    (Books.id neq excludingBookId)
            }
            .limit(1)
            .any()

    fun applyPatch(
        bookId: Int,
        moveToShelfId: Int?, // null = don't move
        currentPage: Int?, // null = unchanged
        finishedAt: Instant?, // set when status becomes FINISHED
        updatedAt: Instant?, // set on any change if you store it
    ): Int =
        Books.update({ Books.id eq bookId }) {
            moveToShelfId?.let { shelf -> it[Books.shelfId] = shelf }
            currentPage?.let { p -> it[Books.currentPage] = p }
            finishedAt?.let { ts -> it[Books.finishedAt] = ts.atOffset(ZoneOffset.UTC) }
            updatedAt?.let { ts -> it[Books.updatedAt] = ts.atOffset(ZoneOffset.UTC) }
        }

    fun getCountsByShelf(userId: Int): Map<Int, Long> {
        return Shelves.leftJoin(
                otherTable = Books,
                onColumn = { Shelves.id },
                otherColumn = { Books.shelfId },
                additionalConstraint = { Books.userId eq userId },
            )
            .select(Shelves.id, Books.id.count())
            .groupBy(Shelves.id)
            .associate { it[Shelves.id] to it[Books.id.count()] }
    }
}

fun ResultRow.toBook() =
    Book(
        id = this[Books.id],
        googleId = this[Books.googleId],
        title = this[Books.title],
        authors = this[Books.authors],
        thumbnailUrl = this[Books.thumbnailUrl],
        currentPage = this[Books.currentPage],
        pageCount = this[Books.pageCount],
        createdAt = this[Books.createdAt].toInstant(),
        updatedAt = this[Books.updatedAt]?.toInstant(),
        finishedAt = this[Books.finishedAt]?.toInstant(),
        userId = this[Books.userId],
        shelfId = this[Books.shelfId],
    )
