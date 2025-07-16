package dev.benkelenski.booked.routes

import dev.benkelenski.booked.domain.AuthPayload
import dev.benkelenski.booked.domain.UserResponse
import dev.benkelenski.booked.domain.requests.LoginRequest
import dev.benkelenski.booked.domain.requests.OAuthRequest
import dev.benkelenski.booked.domain.requests.RegisterRequest
import dev.benkelenski.booked.services.*
import dev.benkelenski.booked.utils.CookieUtils
import org.http4k.core.*
import org.http4k.core.cookie.cookie
import org.http4k.format.Moshi.auto
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

val registerRequestLens = Body.auto<RegisterRequest>().toLens()
val loginRequestLens = Body.auto<LoginRequest>().toLens()
val authRequestLens = Body.auto<OAuthRequest>().toLens()

val userResLens = Body.auto<UserResponse>().toLens()

fun authRoutes(
    registerWithEmail: RegisterWithEmail,
    loginWithEmail: LoginWithEmail,
    authenticateWith: AuthenticateWith,
    refresh: Refresh,
    logout: Logout,
): RoutingHttpHandler {

    return routes(
        "/auth" bind
            routes(
                "/register" bind
                    Method.POST to
                    { request ->
                        val registerRequest = registerRequestLens(request)
                        when (
                            val result =
                                registerWithEmail(
                                    registerRequest.email,
                                    registerRequest.password,
                                    registerRequest.name,
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
                        }
                    },
                "/login" bind
                    Method.POST to
                    { request ->
                        val loginRequest = loginRequestLens(request)
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
}
