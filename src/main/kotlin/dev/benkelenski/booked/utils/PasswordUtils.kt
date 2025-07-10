package dev.benkelenski.booked.utils

import at.favre.lib.crypto.bcrypt.BCrypt

object PasswordUtils {
    fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(12, password.toCharArray())

    fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified
}
