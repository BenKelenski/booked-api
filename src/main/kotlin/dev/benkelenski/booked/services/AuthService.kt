package dev.benkelenski.booked.services

import dev.benkelenski.booked.auth.GoogleAuthProvider
import dev.benkelenski.booked.auth.TokenProvider
import dev.benkelenski.booked.domain.AuthPayload
import dev.benkelenski.booked.domain.SessionResult
import dev.benkelenski.booked.repos.GetOrCreateUserResult
import dev.benkelenski.booked.repos.RefreshTokenRepo
import dev.benkelenski.booked.repos.ShelfRepo
import dev.benkelenski.booked.repos.UserRepo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
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
    private val shelfRepo: ShelfRepo,
    private val googleAuthProvider: GoogleAuthProvider,
    private val tokenProvider: TokenProvider,
) {

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val EMAIL_PROVIDER = "email"
        private const val GOOGLE_PROVIDER = "google"
    }

    /** Register a new user using email and password */
    fun registerWithEmail(email: String, password: String, name: String?): AuthResult =
        try {
            transaction {
                when (
                    val res =
                        userRepo.getOrCreateUser(
                            provider = EMAIL_PROVIDER,
                            providerUserId = email,
                            email = email,
                            name = name,
                            password = password,
                        )
                ) {
                    is GetOrCreateUserResult.Created -> {
                        val newUser = res.user

                        logger.info { "Created user ${newUser.name} (${newUser.id})" }

                        shelfRepo.createDefaultShelves(newUser.id)

                        val (accessToken, refreshToken) = getTokens(newUser.id)

                        AuthResult.Success(
                            session =
                                SessionResult(
                                    user = newUser,
                                    accessToken = accessToken,
                                    refreshToken = refreshToken,
                                )
                        )
                    }
                    is GetOrCreateUserResult.Existing -> {
                        logger.warn { "User with email $email already exists" }
                        return@transaction AuthResult.Failure(
                            "User with email $email already exists"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to register user with email $email" }
            AuthResult.DatabaseError
        }

    /** Log in a user using email and password */
    fun loginWithEmail(email: String, password: String): AuthResult =
        try {
            transaction {
                val user =
                    userRepo.findUserByEmailAndPassword(email, password)
                        ?: return@transaction AuthResult.Failure("Invalid email or password")

                val (accessToken, refreshToken) = getTokens(user.id)

                AuthResult.Success(
                    session =
                        SessionResult(
                            user = user,
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                        )
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to login user with email $email" }
            AuthResult.DatabaseError
        }

    /** Authenticate a user-provided token */
    fun authenticateWith(authPayload: AuthPayload): AuthResult =
        try {
            transaction {
                val provider = authPayload.provider.lowercase()

                val idTokenClaims =
                    when (provider) {
                        GOOGLE_PROVIDER ->
                            googleAuthProvider.authenticate(authPayload.providerToken)
                        else -> null
                    }
                        ?: run {
                            logger.warn {
                                "Failed to authenticate user for provider: ${authPayload.provider}"
                            }
                            return@transaction AuthResult.Failure(
                                "Failed to authenticate user for provider: ${authPayload.provider}"
                            )
                        }

                val user =
                    when (
                        val res =
                            userRepo.getOrCreateUser(
                                provider = provider,
                                providerUserId = idTokenClaims.subject,
                                email = idTokenClaims.email,
                                name = idTokenClaims.name,
                            )
                    ) {
                        is GetOrCreateUserResult.Created -> {
                            logger.info { "Created user ${res.user.name} (${res.user.id})" }
                            shelfRepo.createDefaultShelves(res.user.id)
                            res.user
                        }

                        is GetOrCreateUserResult.Existing -> {
                            logger.info { "Found user ${res.user.name} (${res.user.id})" }
                            res.user
                        }
                    }

                val (accessToken, refreshToken) = getTokens(user.id)

                return@transaction AuthResult.Success(
                    session = SessionResult(user, accessToken, refreshToken)
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to authenticate user" }
            AuthResult.DatabaseError
        }

    fun refresh(refreshToken: String): AuthResult =
        try {
            transaction {
                val tokenId =
                    tokenProvider.getTokenId(refreshToken)
                        ?: return@transaction AuthResult.Failure("Invalid refresh token")

                val userId =
                    refreshTokenRepo.validateAndDelete(refreshToken, tokenId)
                        ?: return@transaction AuthResult.Failure("Failure to delete refresh token")

                val (accessToken, refreshToken) = getTokens(userId)

                val user =
                    userRepo.getUserById(userId)
                        ?: return@transaction AuthResult.Failure("User not found")

                AuthResult.Success(session = SessionResult(user, accessToken, refreshToken))
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to refresh token" }
            AuthResult.DatabaseError
        }

    fun logout(userId: Int) = transaction { refreshTokenRepo.deleteAllForUser(userId) }

    /**
     * Provides a new access and refresh token for a given
     *
     * @param userId the user ID to generate tokens for
     * @return a pair of access and refresh tokens
     */
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

    object DatabaseError : AuthResult()

    data class Failure(val reason: String) : AuthResult()
}
