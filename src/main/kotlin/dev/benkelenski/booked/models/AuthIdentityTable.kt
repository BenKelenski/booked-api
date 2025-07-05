package dev.benkelenski.booked.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

object AuthIdentityTable : Table("auth_identities") {
    val id = integer("id").autoIncrement()
    val userId = reference("user_id", UserTable.id, ReferenceOption.CASCADE)
    val provider = varchar("provider", 50)
    val providerUserId = varchar("provider_user_id", 255)
    val email = varchar("email", 255).nullable()
    val passwordHash = varchar("password_hash", 255).nullable()
    val createdAt = timestampWithTimeZone("created_at")

    init {
        uniqueIndex(provider, providerUserId)
    }
}
