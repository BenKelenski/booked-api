package dev.benkelenski.booked.auth

interface TokenProvider {
    fun generateAccessToken(userId: Int): String

    fun generateRefreshToken(userId: Int, tokenId: String): String

    fun extractUserId(token: String): Int?

    fun isRefreshToken(token: String): Boolean

    fun getTokenId(token: String): String?
}
