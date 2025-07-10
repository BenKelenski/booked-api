package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentDateTime
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object RefreshTokens : Table("refresh_tokens") {
    val id = uuid("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val tokenHash = text("token_hash")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val expiresAt = datetime("expires_at")

    override val primaryKey = PrimaryKey(id)
}
