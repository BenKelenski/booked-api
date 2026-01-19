package dev.benkelenski.booked.repos

// import java.time.Instant
import dev.benkelenski.booked.models.RefreshTokens
import dev.benkelenski.booked.utils.PasswordUtils
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.selectAll

class RefreshTokenRepo {

    /**
     * Stores a hashed refresh token with expiration Returns the UUID string (token ID) to embed in
     * JWT
     */
    fun create(userId: Int, rawToken: String, expiresAt: Instant): String =
        RefreshTokens.insertReturning {
                it[this.id] = UUID.randomUUID()
                it[this.userId] = userId
                it[this.tokenHash] = PasswordUtils.hashRefreshToken(rawToken)
                it[this.expiresAt] = expiresAt.atOffset(ZoneOffset.UTC)
            }
            .single()[RefreshTokens.id]
            .toString()

    /**
     * Validate the given raw opaque token against its stored hash, delete it, and return the
     * associated user ID. Returns null on any failure.
     */
    fun validateAndDelete(userId: Int, rawToken: String): Int? {
        val row =
            RefreshTokens.selectAll()
                .where { RefreshTokens.userId eq userId }
                .firstOrNull {
                    PasswordUtils.verifyRefreshToken(rawToken, it[RefreshTokens.tokenHash])
                } ?: return null

        RefreshTokens.deleteWhere { RefreshTokens.id eq row[RefreshTokens.id] }
        return userId
    }

    /**
     * Delete all refresh tokens for a given user (e.g. on logout or admin revoke). Returns the
     * number of tokens deleted.
     */
    fun deleteAllForUser(userId: Int): Int =
        RefreshTokens.deleteWhere { RefreshTokens.userId eq userId }
}
