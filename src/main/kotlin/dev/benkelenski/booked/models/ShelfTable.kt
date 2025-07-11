package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object ShelfTable : Table("shelves") {
    val id = integer("id").autoIncrement()
    val userId = varchar("user_id", 128)
    val name = varchar("name", 150)
    val description = varchar("description", 250).nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
