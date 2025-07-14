package utils

import dev.benkelenski.booked.auth.TokenProvider

class FakeTokenProvider : TokenProvider {
    override fun generateAccessToken(userId: Int) = "access-$userId"

    override fun generateRefreshToken(userId: Int, tokenId: String) = "refresh-$tokenId"

    override fun extractUserId(token: String): Int? = token.removePrefix("access-").toIntOrNull()

    override fun isRefreshToken(token: String) = token.startsWith("refresh-")

    override fun getTokenId(token: String): String? = token.removePrefix("refresh-")
}
