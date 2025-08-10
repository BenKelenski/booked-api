package dev.benkelenski.booked.auth

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.RSAKeyProvider
import dev.benkelenski.booked.domain.User
import dev.benkelenski.booked.repos.GetOrCreateUserResult
import dev.benkelenski.booked.repos.UserRepo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.base64DecodedArray
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit

class GoogleAuthProvider(
    private val publicKey: String?,
    private val jwksUri: String,
    issuer: String,
    audience: String,
    private val userRepo: UserRepo,
) : AuthProvider {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val algorithm =
        publicKey?.let { publicKey ->
            val keySpec = X509EncodedKeySpec(publicKey.base64DecodedArray())
            val javaPublicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)
            Algorithm.RSA256(javaPublicKey as RSAPublicKey, null)
        }
            ?: run {
                val provider =
                    JwkProviderBuilder(URI.create(jwksUri).toURL())
                        .cached(10, 24, TimeUnit.HOURS)
                        .rateLimited(10, 1, TimeUnit.MINUTES)
                        .build()

                val rsaKeyProvider =
                    object : RSAKeyProvider {
                        override fun getPublicKeyById(keyId: String?) =
                            provider.get(keyId).publicKey as RSAPublicKey

                        override fun getPrivateKey() = null

                        override fun getPrivateKeyId() = null
                    }

                Algorithm.RSA256(rsaKeyProvider)
            }

    private val verifier: JWTVerifier =
        JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()

    private fun verify(token: String): DecodedJWT? {
        return try {
            verifier.verify(token)
        } catch (e: JWTVerificationException) {
            null
        }
    }

    override fun authenticate(idToken: String): User? =
        try {
            transaction {
                val decoded = verify(idToken) ?: return@transaction null

                val providerUserId = decoded.subject ?: return@transaction null
                val email = decoded.getClaim("email").asString()
                val name = decoded.getClaim("name").asString()

                when (
                    val res =
                        userRepo.getOrCreateUser(
                            provider = "google",
                            providerUserId = providerUserId,
                            email = email,
                            name = name,
                        )
                ) {
                    is GetOrCreateUserResult.Created -> {
                        logger.info { "Created user ${res.user.name} (${res.user.id})" }
                        res.user
                    }
                    is GetOrCreateUserResult.Existing -> {
                        logger.info { "Found user ${res.user.name} (${res.user.id})" }
                        res.user
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to authenticate user" }
            null
        }
}
