package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Books : Table("books") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 250)
    val author = varchar("author", 250)
    val createdAt =
        timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val shelfId = reference("shelf_id", Shelves.id, ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(id)
}
