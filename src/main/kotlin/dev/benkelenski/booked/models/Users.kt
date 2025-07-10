package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 255).nullable()
    val name = varchar("name", 255).nullable()
    val createdAt =
        timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
