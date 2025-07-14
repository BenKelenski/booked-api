package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object RefreshTokens : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = integer("user_id").references(Users.id)
    val tokenHash = text("token_hash")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val expiresAt = timestamp("expires_at")

    override val primaryKey = PrimaryKey(id)
}
