package dev.benkelenski.booked.auth

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.RSAKeyProvider
import org.http4k.base64DecodedArray
import java.net.URI
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit

/** alias for [AuthProvider.verify] */
typealias Verify = (token: String) -> String?

class AuthProvider(
    private val publicKey: String?,
    private val jwksUri: String,
    private val issuer: String,
    private val audience: String,
) {

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

    fun verify(token: String): String? {
        return try {
            verifier.verify(token).subject
        } catch (e: JWTVerificationException) {
            null
        }
    }
}
