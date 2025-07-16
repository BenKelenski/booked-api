package dev.benkelenski.booked.repos

// import java.time.Instant
import dev.benkelenski.booked.models.RefreshTokens
import dev.benkelenski.booked.utils.PasswordUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
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

    /**
     * Validate the given raw refresh‐JWT against its stored hash, ensure it hasn’t expired, delete
     * it, and return the associated user ID. Returns null on any failure.
     */
    fun validateAndDelete(rawToken: String, tokenId: String): Int? = transaction {
        // 1) Parse tokenId → UUID
        val uuid = runCatching { UUID.fromString(tokenId) }.getOrNull() ?: return@transaction null

        // 2) Lookup the stored row
        //        val row = RefreshTokens.select { RefreshTokens.id eq uuid }
        //            .singleOrNull() ?: return@transaction null

        val row =
            RefreshTokens.selectAll().where { RefreshTokens.id eq uuid }.singleOrNull()
                ?: return@transaction null

        // 3) Check hash matches
        val storedHash = row[RefreshTokens.tokenHash]
        if (!PasswordUtils.verifyRefreshToken(rawToken, storedHash)) return@transaction null

        // 4) Check not expired
        val expiresAt = row[RefreshTokens.expiresAt]
        if (expiresAt.isBefore(Instant.now())) return@transaction null

        // 5) Delete the used token (rotation)
        RefreshTokens.deleteWhere { RefreshTokens.id eq uuid }

        // 6) Return the userId
        row[RefreshTokens.userId]
    }

    /**
     * Delete all refresh tokens for a given user (e.g. on logout or admin revoke). Returns the
     * number of tokens deleted.
     */
    fun deleteAllForUser(userId: Int): Int = transaction {
        RefreshTokens.deleteWhere { RefreshTokens.userId eq userId }
    }
}
