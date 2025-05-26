package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object ShelfTable : Table("shelf") {
  val id = integer("id")
  val name = varchar("name", 150)
  val description = varchar("description", 250).nullable()
  val createdAt = timestampWithTimeZone("created_at")

  override val primaryKey = PrimaryKey(id)
}
