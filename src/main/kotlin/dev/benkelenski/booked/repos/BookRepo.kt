package dev.benkelenski.booked.repos


import dev.benkelenski.booked.models.Book
import dev.benkelenski.booked.models.BookTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.transaction

class BooksRepo {

    fun getAllBooks() = transaction {
        addLogger(StdOutSqlLogger)
        BookTable.selectAll()
            .map(::mapToBook)
    }


    fun getBookById(id: Int): Book? = transaction {
        addLogger(StdOutSqlLogger)
        BookTable.selectAll()
            .where { BookTable.id eq id }.map(::mapToBook).singleOrNull()
    }

    fun saveBook(title: String, author: String): Book? = transaction {
        addLogger(StdOutSqlLogger)
        BookTable.insertReturning {
            it[BookTable.title] = title
            it[BookTable.author] = author
            it[BookTable.createdAt] = CurrentTimestampWithTimeZone
        }
            .map(::mapToBook)
            .singleOrNull()
    }

    fun deleteBook(id: Int): Int = transaction {
        addLogger(StdOutSqlLogger)
        BookTable.deleteWhere {
            BookTable.id eq id
        }
    }

    private fun mapToBook(row: ResultRow): Book = Book(
        id = row[BookTable.id],
        title = row[BookTable.title],
        author = row[BookTable.author],
        createdAt = row[BookTable.createdAt].toInstant()
    )
}