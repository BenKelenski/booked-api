package dev.benkelenski.booked.services

import dev.benkelenski.booked.auth.GoogleAuthProvider
import dev.benkelenski.booked.auth.TokenProvider
import dev.benkelenski.booked.domain.AuthPayload
import dev.benkelenski.booked.domain.SessionResult
import dev.benkelenski.booked.repos.RefreshTokenRepo
import dev.benkelenski.booked.repos.UserRepo
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.time.Instant

/** alias for [AuthService.registerWithEmail] */
typealias RegisterWithEmail = (email: String, password: String, name: String?) -> AuthResult

/** alias for [AuthService.loginWithEmail] */
typealias LoginWithEmail = (email: String, password: String) -> AuthResult

/** alias for [AuthService.authenticateWith] */
typealias AuthenticateWith = (authPayload: AuthPayload) -> AuthResult

/** alias for [AuthService.refresh] */
typealias Refresh = (refreshToken: String) -> AuthResult

/** alias for [AuthService.logout] */
typealias Logout = (userId: Int) -> Unit

class AuthService(
    private val userRepo: UserRepo,
    private val refreshTokenRepo: RefreshTokenRepo,
    private val googleAuthProvider: GoogleAuthProvider,
    private val tokenProvider: TokenProvider,
) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    /** Register a new user using email and password */
    fun registerWithEmail(email: String, password: String, name: String?): AuthResult {
        val existing = userRepo.findUserByProvider("email", email)
        if (existing != null) {
            logger.warn { "User with email $email already exists" }
            return AuthResult.Failure("User with email $email already exists")
        }

        val newUser =
            userRepo.getOrCreateUser(
                provider = "email",
                providerUserId = email,
                email = email,
                name = name,
                password = password,
            )

        val (accessToken, refreshToken) = getTokens(newUser.id)

        return AuthResult.Success(
            session =
                SessionResult(
                    user = newUser,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                )
        )
    }

    /** Log in a user using email and password */
    fun loginWithEmail(email: String, password: String): AuthResult {
        val user =
            userRepo.findUserByEmailAndPassword(email, password)
                ?: throw IllegalArgumentException("Invalid email or password")

        val (accessToken, refreshToken) = getTokens(user.id)

        return AuthResult.Success(
            session =
                SessionResult(user = user, accessToken = accessToken, refreshToken = refreshToken)
        )
    }

    /** Authenticate a user-provided token */
    fun authenticateWith(authPayload: AuthPayload): AuthResult {
        val user =
            when (authPayload.provider) {
                "google" -> googleAuthProvider.authenticate(authPayload.providerToken)
                else -> null
            }
                ?: run {
                    logger.warn { "Invalid provider ${authPayload.provider}" }
                    return AuthResult.Failure("Invalid provider ${authPayload.provider}")
                }

        val (accessToken, refreshToken) = getTokens(user.id)

        return AuthResult.Success(session = SessionResult(user, accessToken, refreshToken))
    }

    fun refresh(refreshToken: String): AuthResult {

        val tokenId =
            tokenProvider.getTokenId(refreshToken)
                ?: return AuthResult.Failure("Invalid refresh token")

        val userId =
            refreshTokenRepo.validateAndDelete(refreshToken, tokenId)
                ?: return AuthResult.Failure("Failure to delete refresh token")

        val (accessToken, refreshToken) = getTokens(userId)

        val user = userRepo.getUserById(userId) ?: return AuthResult.Failure("User not found")

        return AuthResult.Success(SessionResult(user, accessToken, refreshToken))
    }

    fun logout(userId: Int) {
        refreshTokenRepo.deleteAllForUser(userId)
    }

    private fun getTokens(userId: Int): Pair<String, String> {
        val accessToken = tokenProvider.generateAccessToken(userId)
        val refreshTokenId =
            refreshTokenRepo.create(
                userId = userId,
                rawToken = accessToken,
                expiresAt = Instant.now().plus(Duration.ofDays(7)),
            )
        val refreshToken = tokenProvider.generateRefreshToken(userId, refreshTokenId)

        return accessToken to refreshToken
    }
}

sealed class AuthResult {
    data class Success(val session: SessionResult) : AuthResult()

    data class Failure(val reason: String) : AuthResult()
}
