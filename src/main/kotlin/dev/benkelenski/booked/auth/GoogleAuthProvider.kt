package dev.benkelenski.booked.auth

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.RSAKeyProvider
import dev.benkelenski.booked.domain.AuthRules
import dev.benkelenski.booked.domain.IdTokenClaims
import org.http4k.base64DecodedArray
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
) : AuthProvider {
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

    override fun authenticate(idToken: String): IdTokenClaims? {
        val decoded = verify(idToken) ?: return null

        val subject = decoded.subject ?: return null
        val email = decoded.getClaim("email").asString()
        val name = decoded.getClaim("name").asString()

        return IdTokenClaims(
            subject = subject,
            name = name,
            email = AuthRules.canonicalizeEmail(email),
        )
    }
}
