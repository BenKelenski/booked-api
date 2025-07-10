package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object AuthIdentities : Table("auth_identities") {
    val id = integer("id").autoIncrement()
    val userId = reference("user_id", Users.id, ReferenceOption.CASCADE)
    val provider = varchar("provider", 50)
    val providerUserId = varchar("provider_user_id", 255)
    val email = varchar("email", 255).nullable()
    val passwordHash = text("password_hash").nullable()
    val createdAt =
        timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    init {
        uniqueIndex(provider, providerUserId)
    }
}
