package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object Shelves : Table("shelves") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id")
    val name = varchar("name", 150)
    val description = varchar("description", 250).nullable()
    val createdAt =
        timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
