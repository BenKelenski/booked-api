package dev.benkelenski.booked.models

import dev.benkelenski.booked.domain.ReadingStatus
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Shelves : Table("shelves") {
    val id = integer("id").autoIncrement()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 150)
    val description = varchar("description", 250).nullable()
    val readingStatus =
        enumerationByName("reading_status", 16, ReadingStatus::class).default(ReadingStatus.TO_READ)
    val isDeletable = bool("is_deletable").default(true)
    val createdAt =
        timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    override val primaryKey = PrimaryKey(id)
}
