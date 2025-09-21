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
    checkAuthStatus: CheckAuthStatus,
    logout: Logout,
    authMiddleware: AuthMiddleware,
): RoutingHttpHandler {
    val registerHandler = handler@{ request: Request ->
        val registerRequest =
            try {
                Body.registerReqLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid register request" }
                return@handler Response(Status.BAD_REQUEST)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing or invalid register request",
                                code = ErrorCodes.INVALID_REGISTER_REQUEST,
                                type = ErrorTypes.VALIDATION,
                            )
                    )
            }

        val validationResult = AuthRules.validate(registerRequest)
        if (!validationResult.isValid)
            return@handler Response(Status.UNPROCESSABLE_ENTITY)
                .with(
                    Body.apiErrorLens of
                        ApiError(
                            message = validationResult.errors.first().message,
                            code = ErrorCodes.REGISTRATION_RULE_VIOLATED,
                            type = ErrorTypes.VALIDATION,
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
                Response(Status.BAD_REQUEST)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = result.reason,
                                code = ErrorCodes.EMAIL_ALREADY_REGISTERED,
                                type = ErrorTypes.CONFLICT,
                            )
                    )
            }

            is AuthResult.DatabaseError -> {
                Response(Status.INTERNAL_SERVER_ERROR)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "An error occurred while registering",
                                code = ErrorCodes.INTERNAL_SERVER_ERROR,
                                type = ErrorTypes.SYSTEM,
                            )
                    )
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
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing or invalid login request",
                                code = ErrorCodes.INVALID_LOGIN_REQUEST,
                                type = ErrorTypes.VALIDATION,
                            )
                    )
            }

        val email = AuthRules.canonicalizeEmail(loginRequest.email)
        val emailValidationResult = AuthRules.validateEmail(email)
        if (emailValidationResult != null) {
            return@handler Response(Status.BAD_REQUEST)
                .with(
                    Body.apiErrorLens of
                        ApiError(
                            message = emailValidationResult.message,
                            code = ErrorCodes.INVALID_EMAIL_ADDRESS,
                            type = ErrorTypes.VALIDATION,
                        )
                )
        }

        val password = loginRequest.password
        if (password.isEmpty() || password.length > 1024) {
            return@handler Response(Status.BAD_REQUEST)
                .with(
                    Body.apiErrorLens of
                        ApiError(
                            message = "Invalid password",
                            code = ErrorCodes.INVALID_PASSWORD,
                            type = ErrorTypes.VALIDATION,
                        )
                )
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
                Response(Status.UNAUTHORIZED)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = result.reason,
                                code = ErrorCodes.INVALID_CREDENTIALS,
                                type = ErrorTypes.AUTHENTICATION,
                            )
                    )
            }

            is AuthResult.DatabaseError -> {
                Response(Status.INTERNAL_SERVER_ERROR)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "An error occurred while logging in",
                                code = ErrorCodes.INTERNAL_SERVER_ERROR,
                                type = ErrorTypes.SYSTEM,
                            )
                    )
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
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing or invalid OAuth request",
                                code = ErrorCodes.INVALID_OAUTH_REQUEST,
                                type = ErrorTypes.VALIDATION,
                            )
                    )
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
                Response(Status.UNAUTHORIZED)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = result.reason,
                                code = ErrorCodes.INVALID_OAUTH_TOKEN,
                                type = ErrorTypes.AUTHENTICATION,
                            )
                    )
            }

            is AuthResult.DatabaseError -> {
                Response(Status.INTERNAL_SERVER_ERROR)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "An error occurred while authenticating with OAuth",
                                code = ErrorCodes.INTERNAL_SERVER_ERROR,
                                type = ErrorTypes.SYSTEM,
                            )
                    )
            }
        }
    }

    val refreshHandler = handler@{ request: Request ->
        val rawRefresh =
            request.cookie("refresh_token")?.value
                ?: return@handler Response(Status.UNAUTHORIZED)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing refresh token",
                                code = ErrorCodes.MISSING_REFRESH_TOKEN,
                                type = ErrorTypes.AUTHENTICATION,
                            )
                    )

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
                Response(Status.UNAUTHORIZED)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = result.reason,
                                code = ErrorCodes.INVALID_REFRESH_TOKEN,
                                type = ErrorTypes.AUTHENTICATION,
                            )
                    )
            }

            is AuthResult.DatabaseError -> {
                Response(Status.INTERNAL_SERVER_ERROR)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "An error occurred while refreshing the token",
                                code = ErrorCodes.INTERNAL_SERVER_ERROR,
                                type = ErrorTypes.SYSTEM,
                            )
                    )
            }
        }
    }

    val statusCheckHandler = { request: Request ->
        val accessToken = request.cookie("access_token")?.value
        val refreshToken = request.cookie("refresh_token")?.value

        checkAuthStatus(accessToken, refreshToken).let { (authResponse, accessToken) ->
            if (!authResponse.isAuthenticated) {
                Response(Status.OK)
                    .with(Body.authStatusResLens of authResponse)
                    .cookie(CookieUtils.expireCookie(HttpConstants.ACCESS_TOKEN_COOKIE))
                    .cookie(CookieUtils.expireCookie(HttpConstants.REFRESH_TOKEN_COOKIE))
            }
            Response(Status.OK).with(Body.authStatusResLens of authResponse).let {
                if (accessToken != null) it.cookie(CookieUtils.accessTokenCookie(accessToken))
                else it
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
        "/auth/status" bind Method.GET to statusCheckHandler,
        routes("/auth/logout" bind Method.POST to logoutHandler).withFilter(authMiddleware),
    )
}
