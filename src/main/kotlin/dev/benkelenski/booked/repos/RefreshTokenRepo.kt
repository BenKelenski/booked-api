package dev.benkelenski.booked.repos

// import java.time.Instant
import dev.benkelenski.booked.models.RefreshTokens
import dev.benkelenski.booked.utils.PasswordUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class RefreshTokenRepo {

    /**
     * Stores a hashed refresh token with expiration Returns the UUID string (token ID) to embed in
     * JWT
     */
    fun create(userId: Int, rawToken: String, expiresAt: Instant): String = transaction {
        val id = UUID.randomUUID()
        val hash = PasswordUtils.hashRefreshToken(rawToken)

        RefreshTokens.insert {
            it[this.id] = id
            it[this.userId] = userId
            it[this.tokenHash] = hash
            it[this.expiresAt] = expiresAt
        }

        id.toString()
    }
}
