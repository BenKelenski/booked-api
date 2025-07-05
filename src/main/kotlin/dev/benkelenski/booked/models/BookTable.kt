package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object BookTable : Table("books") {
    val id = integer("id").autoIncrement()
    val userId = varchar("user_id", 128)
    val title = varchar("title", 250)
    val author = varchar("author", 250)
    val createdAt = timestampWithTimeZone("created_at")
    val shelfId = reference("shelf_id", ShelfTable.id, ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(id)
}
