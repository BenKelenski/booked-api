package dev.benkelenski.booked.auth

import dev.benkelenski.booked.utils.JwtUtils

class JwtTokenProvider : TokenProvider {

    override fun generateAccessToken(userId: Int): String = JwtUtils.generateAccessToken(userId)

    override fun generateRefreshToken(userId: Int, tokenId: String): String =
        JwtUtils.generateRefreshToken(userId, tokenId)

    override fun extractUserId(token: String): Int? = JwtUtils.extractUserId(token)

    override fun isRefreshToken(token: String): Boolean = JwtUtils.isRefreshToken(token)

    override fun getTokenId(token: String): String? = JwtUtils.getTokenId(token)
}
