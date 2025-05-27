package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object BookTable : Table("book") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 250)
    val author = varchar("author", 250)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
