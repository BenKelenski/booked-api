package dev.benkelenski.booked.repos

// import java.time.Instant
import dev.benkelenski.booked.models.RefreshTokens
import dev.benkelenski.booked.utils.PasswordUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

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
     * Validate the given raw refresh‐JWT against its stored hash, ensure it hasn’t expired, delete
     * it, and return the associated user ID. Returns null on any failure.
     */
    fun validateAndDelete(rawToken: String, tokenId: String): Int? {
        // 1) Parse tokenId → UUID
        val uuid = runCatching { UUID.fromString(tokenId) }.getOrNull() ?: return null

        // 2) Lookup the stored row
        val row =
            RefreshTokens.selectAll().where { RefreshTokens.id eq uuid }.singleOrNull()
                ?: return null

        // 3) Check hash matches
        val storedHash = row[RefreshTokens.tokenHash]
        if (!PasswordUtils.verifyRefreshToken(rawToken, storedHash)) return null

        // 4) Check not expired
        val expiresAt = row[RefreshTokens.expiresAt]
        if (expiresAt.isBefore(OffsetDateTime.now())) return null

        // 5) Delete the used token (rotation)
        RefreshTokens.deleteWhere { RefreshTokens.id eq uuid }

        // 6) Return the userId
        return row[RefreshTokens.userId]
    }

    /**
     * Delete all refresh tokens for a given user (e.g. on logout or admin revoke). Returns the
     * number of tokens deleted.
     */
    fun deleteAllForUser(userId: Int): Int =
        RefreshTokens.deleteWhere { RefreshTokens.userId eq userId }
}
