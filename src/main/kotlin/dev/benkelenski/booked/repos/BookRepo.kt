package dev.benkelenski.booked.repos

import dev.benkelenski.booked.domain.Book
import dev.benkelenski.booked.models.Books
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class BookRepo {

    fun getAllBooksByUser(userId: Int): List<Book> = transaction {
        Books.selectAll().where { Books.userId eq userId }.map { it.toBook() }
    }

    fun getBookById(bookId: Int): Book? = transaction {
        Books.selectAll().where { Books.id eq bookId }.map { it.toBook() }.singleOrNull()
    }

    fun saveBook(
        userId: Int,
        shelfId: Int,
        googleId: String,
        title: String,
        authors: List<String>,
        thumbnailUrl: String? = null,
    ): Book? = transaction {
        Books.insertReturning {
                it[this.userId] = userId
                it[this.googleId] = googleId
                it[this.title] = title
                it[this.authors] = authors
                it[this.shelfId] = shelfId
                it[this.thumbnailUrl] = thumbnailUrl
            }
            .map { it.toBook() }
            .singleOrNull()
    }

    fun deleteByIdAndUser(userId: Int, bookId: Int): Int = transaction {
        Books.deleteWhere { (Books.userId eq userId) and (Books.id eq bookId) }
    }

    fun existsById(id: Int): Boolean = transaction {
        Books.selectAll().where { Books.id eq id }.limit(1).any()
    }

    fun findAllByShelfAndUser(shelfId: Int, userId: Int): List<Book> = transaction {
        Books.selectAll()
            .where { (Books.userId eq userId) and (Books.shelfId eq shelfId) }
            .map { it.toBook() }
    }

    fun existsByShelfAndGoogleId(shelfId: Int, googleId: String): Boolean = transaction {
        Books.selectAll()
            .where { (Books.shelfId eq shelfId) and (Books.googleId eq googleId) }
            .limit(1)
            .any()
    }
}

fun ResultRow.toBook() =
    Book(
        id = this[Books.id],
        googleId = this[Books.googleId],
        title = this[Books.title],
        authors = this[Books.authors],
        thumbnailUrl = this[Books.thumbnailUrl],
        createdAt = this[Books.createdAt].toInstant(),
        userId = this[Books.userId],
        shelfId = this[Books.shelfId],
    )
