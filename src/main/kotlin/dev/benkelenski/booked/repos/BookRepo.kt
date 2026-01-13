package dev.benkelenski.booked.repos

import dev.benkelenski.booked.domain.Book
import dev.benkelenski.booked.models.Books
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.time.ZoneOffset

class BookRepo {

    fun findByIdAndUser(bookId: Int, userId: Int): Book? =
        Books.selectAll()
            .where { (Books.id eq bookId) and (Books.userId eq userId) }
            .map { it.toBook() }
            .singleOrNull()

    fun fetchAllBooksForUser(userId: Int, shelves: List<Int>): List<Book> {
        val query = Books.selectAll().where { Books.userId eq userId }

        if (shelves.isNotEmpty()) {
            query.andWhere { Books.shelfId inList shelves }
        }

        return query.map { it.toBook() }
    }

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

    fun existsByGoogleIdAndUser(googleId: String, userId: Int): Boolean =
        Books.selectAll()
            .where { (Books.userId eq userId) and (Books.googleId eq googleId) }
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

    fun updateBook(
        book: Book,
    ): Book? =
        Books.updateReturning(where = { Books.id eq book.id }) {
                it[Books.shelfId] = book.shelfId
                it[Books.currentPage] = book.currentPage
                it[Books.rating] = book.rating
                it[Books.review] = book.review
                it[Books.updatedAt] = Instant.now().atOffset(ZoneOffset.UTC)
                it[Books.finishedAt] = book.finishedAt?.atOffset(ZoneOffset.UTC)
            }
            .map { it.toBook() }
            .singleOrNull()
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
        rating = this[Books.rating],
        review = this[Books.review],
        createdAt = this[Books.createdAt].toInstant(),
        updatedAt = this[Books.updatedAt]?.toInstant(),
        finishedAt = this[Books.finishedAt]?.toInstant(),
        userId = this[Books.userId],
        shelfId = this[Books.shelfId],
    )
