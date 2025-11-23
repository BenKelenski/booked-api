package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Books : Table("books") {
    val id = integer("id").autoIncrement()
    val googleId = text("google_id")
    val title = text("title")
    val authors = array<String>("authors")
    val thumbnailUrl = text("thumbnail_url").nullable()
    val currentPage = integer("current_page").nullable()
    val pageCount = integer("page_count").nullable()
    val rating = integer("rating").nullable()
    val review = text("review").nullable()
    val createdAt =
        timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").nullable()
    val finishedAt = timestampWithTimeZone("finished_at").nullable()
    val userId = reference("user_id", Users.id, ReferenceOption.CASCADE)
    val shelfId = reference("shelf_id", Shelves.id, ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_books_user_google", userId, googleId)
    }
}
