package dev.benkelenski.booked.repos

import dev.benkelenski.booked.domain.Book
import dev.benkelenski.booked.models.Books
import dev.benkelenski.booked.models.Shelves
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class BookRepo {

    fun getAllBooks(): List<Book> = transaction { Books.selectAll().map { it.toBook() } }

    fun getBookById(id: Int): Book? = transaction {
        Books.selectAll().where { Books.id eq id }.map { it.toBook() }.singleOrNull()
    }

    fun saveBook(title: String, author: String, shelfId: Int): Book? = transaction {
        Books.insertReturning {
                it[Books.title] = title
                it[Books.author] = author
                it[Books.shelfId] = shelfId
            }
            .map { it.toBook() }
            .singleOrNull()
    }

    fun deleteByIdAndUser(userId: Int, bookId: Int): Int = transaction {
        (Shelves.join(Books, JoinType.INNER, Shelves.id, Books.shelfId)).delete(Books) {
            (Books.id eq bookId) and (Shelves.userId eq userId)
        }
    }

    fun existsById(id: Int): Boolean = transaction {
        Books.selectAll().where { Books.id eq id }.any()
    }
}

fun ResultRow.toBook() =
    Book(
        id = this[Books.id],
        title = this[Books.title],
        author = this[Books.author],
        createdAt = this[Books.createdAt].toInstant(),
        shelfId = this[Books.shelfId],
    )
