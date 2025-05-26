package dev.benkelenski.booked.repos

import dev.benkelenski.booked.models.Shelf
import dev.benkelenski.booked.models.ShelfTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.transaction

class ShelfRepo {

  fun getAllShelves(): List<Shelf> = transaction {
    addLogger(StdOutSqlLogger)
    ShelfTable.selectAll().map { it.toShelf() }
  }

  fun getShelfById(id: Int): Shelf? = transaction {
    addLogger(StdOutSqlLogger)
    ShelfTable.selectAll().where { ShelfTable.id eq id }.map { it.toShelf() }.singleOrNull()
  }

  fun addShelf(name: String, description: String?): Shelf? = transaction {
    addLogger(StdOutSqlLogger)
    ShelfTable.insertReturning {
        it[ShelfTable.name] = name
        it[ShelfTable.description] = description
        it[ShelfTable.createdAt] = CurrentTimestampWithTimeZone
      }
      .map { it.toShelf() }
      .singleOrNull()
  }

  fun deleteShelf(id: Int): Int = transaction {
    addLogger(StdOutSqlLogger)
    ShelfTable.deleteWhere { ShelfTable.id eq id }
  }
}

fun ResultRow.toShelf() =
  Shelf(
    id = this[ShelfTable.id],
    name = this[ShelfTable.name],
    description = this[ShelfTable.description],
    createdAt = this[ShelfTable.createdAt].toInstant(),
  )
