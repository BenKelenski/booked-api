package dev.benkelenski.booked.routes

import dev.benkelenski.booked.constants.ErrorCodes
import dev.benkelenski.booked.constants.ErrorTypes
import dev.benkelenski.booked.constants.HttpConstants
import dev.benkelenski.booked.domain.*
import dev.benkelenski.booked.domain.responses.ApiError
import dev.benkelenski.booked.domain.responses.UserResponse
import dev.benkelenski.booked.middleware.AuthMiddleware
import dev.benkelenski.booked.middleware.authHandler
import dev.benkelenski.booked.services.*
import dev.benkelenski.booked.utils.CookieUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.*
import org.http4k.core.cookie.cookie
import org.http4k.format.Moshi.auto
import org.http4k.lens.LensFailure
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

private val logger = KotlinLogging.logger {}

fun authRoutes(
    registerWithEmail: RegisterWithEmail,
    loginWithEmail: LoginWithEmail,
    authenticateWith: AuthenticateWith,
    refresh: Refresh,
    logout: Logout,
    authMiddleware: AuthMiddleware,
): RoutingHttpHandler {
    val registerHandler = handler@{ request: Request ->
        val registerRequest =
            try {
                Body.registerReqLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid register request." }
                return@handler Response(Status.BAD_REQUEST)
            }

        val validationResult = AuthRules.validate(registerRequest)
        if (!validationResult.isValid)
            return@handler Response(Status.UNPROCESSABLE_ENTITY)
                .with(
                    Body.auto<Map<String, Any>>().toLens() of
                        mapOf(
                            "message" to "Validation failed.",
                            "errors" to validationResult.errors,
                        )
                )

        when (
            val result =
                registerWithEmail(
                    AuthRules.canonicalizeEmail(registerRequest.email),
                    registerRequest.password,
                    registerRequest.name,
                )
        ) {
            is AuthResult.Success -> {
                val s = result.session
                Response(Status.OK)
                    .header("Cache-Control", "no-store")
                    .cookie(CookieUtils.accessTokenCookie(s.accessToken))
                    .cookie(CookieUtils.refreshTokenCookie(s.refreshToken))
                    .with(Body.userResLens of UserResponse.from(s.user))
            }

            is AuthResult.Failure -> {
                Response(Status.BAD_REQUEST).body(result.reason)
            }

            is AuthResult.DatabaseError -> {
                Response(Status.INTERNAL_SERVER_ERROR)
            }
        }
    }

    val loginHandler = handler@{ request: Request ->
        val loginRequest =
            try {
                Body.loginReqLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid login request." }
                return@handler Response(Status.BAD_REQUEST)
            }

        val email = AuthRules.canonicalizeEmail(loginRequest.email)
        val emailValidationResult = AuthRules.validateEmail(email)
        if (emailValidationResult != null) {
            return@handler Response(Status.BAD_REQUEST).body(emailValidationResult.message)
        }

        val password = loginRequest.password
        if (password.isEmpty() || password.length > 1024) {
            return@handler Response(Status.BAD_REQUEST)
        }

        when (val result = loginWithEmail(email, password)) {
            is AuthResult.Success -> {
                val s = result.session
                Response(Status.OK)
                    .header("Cache-Control", "no-store")
                    .cookie(CookieUtils.accessTokenCookie(s.accessToken))
                    .cookie(CookieUtils.refreshTokenCookie(s.refreshToken))
                    .with(Body.userResLens of UserResponse.from(s.user))
            }

            is AuthResult.Failure -> {
                Response(Status.BAD_REQUEST).body(result.reason)
            }

            is AuthResult.DatabaseError -> {
                Response(Status.INTERNAL_SERVER_ERROR)
            }
        }
    }

    val oauthHandler = handler@{ request: Request ->
        val authRequest =
            try {
                Body.oauthReqLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid OAuth request." }
                return@handler Response(Status.BAD_REQUEST)
            }

        when (val result = authenticateWith(AuthPayload(authRequest.provider, authRequest.token))) {
            is AuthResult.Success -> {
                val s = result.session
                Response(Status.OK)
                    .header("Cache-Control", "no-store")
                    .cookie(CookieUtils.accessTokenCookie(s.accessToken))
                    .cookie(CookieUtils.refreshTokenCookie(s.refreshToken))
                    .with(Body.userResLens of UserResponse.from(s.user))
            }

            is AuthResult.Failure -> {
                Response(Status.BAD_REQUEST).body(result.reason)
            }

            is AuthResult.DatabaseError -> {
                Response(Status.INTERNAL_SERVER_ERROR)
            }
        }
    }

    val refreshHandler = handler@{ request: Request ->
        val rawRefresh =
            request.cookie("refresh_token")?.value ?: return@handler Response(Status.UNAUTHORIZED)

        when (val result = refresh(rawRefresh)) {
            is AuthResult.Success -> {
                val s = result.session
                Response(Status.OK)
                    .header("Cache-Control", "no-store")
                    .cookie(CookieUtils.accessTokenCookie(s.accessToken))
                    .cookie(CookieUtils.refreshTokenCookie(s.refreshToken))
                    .with(Body.userResLens of UserResponse.from(s.user))
            }

            is AuthResult.Failure -> {
                Response(Status.UNAUTHORIZED).body(result.reason)
            }

            is AuthResult.DatabaseError -> {
                Response(Status.INTERNAL_SERVER_ERROR)
            }
        }
    }

    val logoutHandler = authHandler { userId: Int, _ ->
        try {
            logout(userId)
            Response(Status.OK)
                .cookie(CookieUtils.expireCookie(HttpConstants.ACCESS_TOKEN_COOKIE))
                .cookie(CookieUtils.expireCookie(HttpConstants.REFRESH_TOKEN_COOKIE))
        } catch (e: Exception) {
            Response(Status.INTERNAL_SERVER_ERROR)
                .with(
                    Body.apiErrorLens of
                        ApiError(
                            message = "An error occurred while logging out",
                            code = ErrorCodes.INTERNAL_SERVER_ERROR,
                            type = ErrorTypes.SYSTEM,
                        )
                )
        }
    }

    return routes(
        "/auth/register" bind Method.POST to registerHandler,
        "/auth/login" bind Method.POST to loginHandler,
        "/auth/oauth" bind Method.POST to oauthHandler,
        "/auth/refresh" bind Method.POST to refreshHandler,
        routes("/auth/logout" bind Method.POST to logoutHandler).withFilter(authMiddleware),
    )
}
