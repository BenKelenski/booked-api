package dev.benkelenski.booked.utils

import at.favre.lib.crypto.bcrypt.BCrypt
import java.security.MessageDigest

object PasswordUtils {
    fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(12, password.toCharArray())

    fun hashRefreshToken(rawToken: String): String {
        val sha256 =
            MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))

        val hex = sha256.joinToString("") { "%02x".format(it) }

        return BCrypt.withDefaults().hashToString(12, hex.toCharArray())
    }

    fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified

    fun verifyRefreshToken(rawToken: String, storedHash: String): Boolean {
        val sha256 =
            MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))

        val hex = sha256.joinToString("") { byte -> "%02x".format(byte) }

        return BCrypt.verifyer().verify(hex.toCharArray(), storedHash).verified
    }
}
