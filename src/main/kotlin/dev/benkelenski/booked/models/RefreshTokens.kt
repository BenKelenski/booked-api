package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object RefreshTokens : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = integer("user_id").references(Users.id).index("idx_refresh_tokens_user_id")
    val tokenHash = text("token_hash")
    val createdAt =
        timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val expiresAt = timestampWithTimeZone("expires_at")

    override val primaryKey = PrimaryKey(id)
}
