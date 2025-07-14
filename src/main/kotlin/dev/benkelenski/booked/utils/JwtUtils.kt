package dev.benkelenski.booked.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

object JwtUtils {
    private const val ISSUER = "booked-app"
    private const val ACCESS_EXPIRATION_MS = 15 * 60 * 1000 // 15 minutes
    private const val REFRESH_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

    private val privateKey: RSAPrivateKey = loadRSAPrivateKey("keys/private_key.pem")
    private val publicKey: RSAPublicKey = loadRSAPublicKey("keys/public_key.pem")
    private val algorithm = Algorithm.RSA256(publicKey, privateKey)

    fun generateAccessToken(userId: Int): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withSubject(userId.toString())
            .withClaim("type", "access")
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + ACCESS_EXPIRATION_MS))
            .sign(algorithm)

    fun generateRefreshToken(userId: Int, tokenId: String): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withSubject(userId.toString())
            .withClaim("type", "refresh")
            .withClaim("token_id", tokenId)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + REFRESH_EXPIRATION_MS))
            .sign(algorithm)

    fun verifyToken(token: String): DecodedJWT? =
        try {
            JWT.require(algorithm).withIssuer(ISSUER).build().verify(token).takeIf {
                it.expiresAt.after(Date())
            }
        } catch (e: Exception) {
            null
        }

    fun extractUserId(token: String): Int? = verifyToken(token)?.subject?.toIntOrNull()

    fun isRefreshToken(token: String): Boolean =
        verifyToken(token)?.getClaim("type")?.asString() == "refresh"

    fun getTokenId(token: String): String? = verifyToken(token)?.getClaim("token_id")?.asString()

    private fun loadRSAPrivateKey(path: String): RSAPrivateKey {
        val content =
            Files.readString(Paths.get(path))
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s+".toRegex(), "")
        val decoded = Base64.getDecoder().decode(content)
        val spec = PKCS8EncodedKeySpec(decoded)
        return KeyFactory.getInstance("RSA").generatePrivate(spec) as RSAPrivateKey
    }

    private fun loadRSAPublicKey(path: String): RSAPublicKey {
        val content =
            Files.readString(Paths.get(path))
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s+".toRegex(), "")
        val decoded = Base64.getDecoder().decode(content)
        val spec = X509EncodedKeySpec(decoded)
        return KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
    }
}
