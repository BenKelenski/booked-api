package dev.benkelenski.booked.repos

import dev.benkelenski.booked.models.Book
import dev.benkelenski.booked.models.BookTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.transaction

class BooksRepo {

  fun getAllBooks(): List<Book> = transaction {
    addLogger(StdOutSqlLogger)
    BookTable.selectAll().map { it.toBook() }
  }

  fun getBookById(id: Int): Book? = transaction {
    addLogger(StdOutSqlLogger)
    BookTable.selectAll().where { BookTable.id eq id }.map { it.toBook() }.singleOrNull()
  }

  fun saveBook(title: String, author: String): Book? = transaction {
    addLogger(StdOutSqlLogger)
    BookTable.insertReturning {
        it[BookTable.title] = title
        it[BookTable.author] = author
        it[BookTable.createdAt] = CurrentTimestampWithTimeZone
      }
      .map { it.toBook() }
      .singleOrNull()
  }

  fun deleteBook(id: Int): Int = transaction {
    addLogger(StdOutSqlLogger)
    BookTable.deleteWhere { BookTable.id eq id }
  }
}

fun ResultRow.toBook() =
  Book(
    id = this[BookTable.id],
    title = this[BookTable.title],
    author = this[BookTable.author],
    createdAt = this[BookTable.createdAt].toInstant(),
  )
