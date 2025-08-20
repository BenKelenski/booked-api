package dev.benkelenski.booked.routes

import dev.benkelenski.booked.domain.AuthPayload
import dev.benkelenski.booked.domain.AuthRules
import dev.benkelenski.booked.domain.requests.LoginRequest
import dev.benkelenski.booked.domain.requests.OAuthRequest
import dev.benkelenski.booked.domain.requests.RegisterRequest
import dev.benkelenski.booked.domain.responses.UserResponse
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

val registerRequestLens = Body.auto<RegisterRequest>().toLens()
val loginRequestLens = Body.auto<LoginRequest>().toLens()
val authRequestLens = Body.auto<OAuthRequest>().toLens()

val userResLens = Body.auto<UserResponse>().toLens()

private val logger = KotlinLogging.logger {}

fun authRoutes(
    registerWithEmail: RegisterWithEmail,
    loginWithEmail: LoginWithEmail,
    authenticateWith: AuthenticateWith,
    refresh: Refresh,
    logout: Logout,
): RoutingHttpHandler =
    routes(
        "/auth" bind
            routes(
                "/register" bind
                    Method.POST to
                    { request ->
                        val registerRequest =
                            try {
                                registerRequestLens(request)
                            } catch (e: LensFailure) {
                                logger.error(e) { "Missing or invalid register request." }
                                return@to Response(Status.BAD_REQUEST)
                            }

                        val validationResult = AuthRules.validate(registerRequest)
                        if (!validationResult.isValid)
                            return@to Response(Status.UNPROCESSABLE_ENTITY)
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
                                    registerRequest.email,
                                    registerRequest.password,
                                    registerRequest.displayName,
                                )
                        ) {
                            is AuthResult.Success -> {
                                val s = result.session
                                Response(Status.OK)
                                    .cookie(CookieUtils.accessTokenCookie(s.accessToken))
                                    .cookie(CookieUtils.refreshTokenCookie(s.refreshToken))
                                    .with(userResLens of UserResponse.from(s.user))
                            }
                            is AuthResult.Failure -> {
                                Response(Status.BAD_REQUEST).body(result.reason)
                            }
                            is AuthResult.DatabaseError -> {
                                Response(Status.INTERNAL_SERVER_ERROR)
                            }
                        }
                    },
                "/login" bind
                    Method.POST to
                    { request ->
                        val loginRequest =
                            try {
                                loginRequestLens(request)
                            } catch (e: LensFailure) {
                                logger.error(e) { "Missing or invalid login request." }
                                return@to Response(Status.BAD_REQUEST)
                            }

                        val email = loginRequest.email.lowercase().trim()
                        if (
                            email.isEmpty() ||
                                email.length > 254 ||
                                !AuthRules.matchesEmailRegex(email)
                        ) {
                            return@to Response(Status.BAD_REQUEST)
                        }

                        val password = loginRequest.password
                        if (password.isEmpty() || password.length > 1024) {
                            return@to Response(Status.BAD_REQUEST)
                        }

                        when (
                            val result = loginWithEmail(loginRequest.email, loginRequest.password)
                        ) {
                            is AuthResult.Success -> {
                                val s = result.session
                                Response(Status.OK)
                                    .cookie(CookieUtils.accessTokenCookie(s.accessToken))
                                    .cookie(CookieUtils.refreshTokenCookie(s.refreshToken))
                                    .with(userResLens of UserResponse.from(s.user))
                            }
                            is AuthResult.Failure -> {
                                Response(Status.BAD_REQUEST).body(result.reason)
                            }
                            is AuthResult.DatabaseError -> {
                                Response(Status.INTERNAL_SERVER_ERROR)
                            }
                        }
                    },
                "/oauth" bind
                    Method.POST to
                    { request ->
                        val authRequest = authRequestLens(request)

                        when (
                            val result =
                                authenticateWith(
                                    AuthPayload(authRequest.provider, authRequest.token)
                                )
                        ) {
                            is AuthResult.Success -> {
                                val s = result.session
                                Response(Status.OK)
                                    .cookie(CookieUtils.accessTokenCookie(s.accessToken))
                                    .cookie(CookieUtils.refreshTokenCookie(s.refreshToken))
                                    .with(userResLens of UserResponse.from(s.user))
                            }
                            is AuthResult.Failure -> {
                                Response(Status.BAD_REQUEST).body(result.reason)
                            }
                            is AuthResult.DatabaseError -> {
                                Response(Status.INTERNAL_SERVER_ERROR)
                            }
                        }
                    },
                "/refresh" bind
                    Method.POST to
                    { request ->
                        val rawRefresh =
                            request.cookie("refresh_token")?.value
                                ?: return@to Response(Status.UNAUTHORIZED)

                        when (val result = refresh(rawRefresh)) {
                            is AuthResult.Success -> {
                                val s = result.session
                                Response(Status.OK)
                                    .cookie(CookieUtils.accessTokenCookie(s.accessToken))
                                    .cookie(CookieUtils.refreshTokenCookie(s.refreshToken))
                                    .with(userResLens of UserResponse.from(s.user))
                            }
                            is AuthResult.Failure -> {
                                Response(Status.UNAUTHORIZED).body(result.reason)
                            }
                            is AuthResult.DatabaseError -> {
                                Response(Status.INTERNAL_SERVER_ERROR)
                            }
                        }
                    },
                "/logout" bind
                    Method.POST to
                    { request ->
                        val userId = request.header("X-User-Id")!!.toInt()
                        logout(userId)
                        Response(Status.OK)
                            .cookie(CookieUtils.expireCookie("access_token"))
                            .cookie(CookieUtils.expireCookie("refresh_token"))
                    },
            )
    )
