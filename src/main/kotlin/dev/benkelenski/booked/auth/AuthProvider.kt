package dev.benkelenski.booked.auth

import dev.benkelenski.booked.domain.User

/// ** alias for [AuthProvider.verifyToken] */
// typealias Verify = (token: String) -> Int?
//
/// ** alias for [AuthProvider.generateToken] */
// typealias GenerateToken = (userId: Int) -> String

interface AuthProvider {
    fun authenticate(idToken: String): SessionResult?

    data class SessionResult(val user: User, val accessToken: String, val refreshToken: String)
}
