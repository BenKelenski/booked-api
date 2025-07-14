package dev.benkelenski.booked.auth

import dev.benkelenski.booked.domain.User

interface AuthProvider {
    fun authenticate(idToken: String): User?
}
