package dev.benkelenski.booked.repos

import dev.benkelenski.booked.domain.Book
import dev.benkelenski.booked.models.Books
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class BookRepo {

    fun getAllBooks(): List<Book> = transaction { Books.selectAll().map { it.toBook() } }

    fun getBookById(id: Int): Book? = transaction {
        Books.selectAll().where { Books.id eq id }.map { it.toBook() }.singleOrNull()
    }

    fun saveBook(userId: Int, title: String, author: String, shelfId: Int): Book? = transaction {
        Books.insertReturning {
                it[Books.userId] = userId
                it[Books.title] = title
                it[Books.author] = author
                it[Books.shelfId] = shelfId
            }
            .map { it.toBook() }
            .singleOrNull()
    }

    fun deleteBook(id: Int): Int = transaction { Books.deleteWhere { Books.id eq id } }
}

fun ResultRow.toBook() =
    Book(
        id = this[Books.id],
        userId = this[Books.userId],
        title = this[Books.title],
        author = this[Books.author],
        createdAt = this[Books.createdAt].toInstant(),
        shelfId = this[Books.shelfId],
    )
