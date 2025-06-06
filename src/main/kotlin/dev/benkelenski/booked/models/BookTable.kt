package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object BookTable : Table("book") {
    val id = integer("id").autoIncrement()
    val userId = varchar(name = "user_id", length = 128)
    val title = varchar("title", 250)
    val author = varchar("author", 250)
    val createdAt = timestampWithTimeZone("created_at")
    val shelfId = reference("shelf_id", ShelfTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(id)
}
