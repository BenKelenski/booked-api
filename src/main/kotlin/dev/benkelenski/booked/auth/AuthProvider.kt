package dev.benkelenski.booked.auth

import dev.benkelenski.booked.domain.IdTokenClaims

interface AuthProvider {
    fun authenticate(idToken: String): IdTokenClaims?
}
