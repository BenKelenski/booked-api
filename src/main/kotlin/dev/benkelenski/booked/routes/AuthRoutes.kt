package dev.benkelenski.booked.routes

import dev.benkelenski.booked.auth.AuthProvider
import dev.benkelenski.booked.domain.AuthPayload
import dev.benkelenski.booked.domain.UserResponse
import dev.benkelenski.booked.domain.requests.LoginRequest
import dev.benkelenski.booked.domain.requests.OAuthRequest
import dev.benkelenski.booked.domain.requests.RegisterRequest
import dev.benkelenski.booked.services.AuthenticateOrRegister
import dev.benkelenski.booked.services.LoginWithEmail
import dev.benkelenski.booked.services.RegisterWithEmail
import dev.benkelenski.booked.utils.JwtUtils
import org.http4k.core.*
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
    authenticateOrRegister: AuthenticateOrRegister,
    authProviders: Map<String, AuthProvider>,
): RoutingHttpHandler {

    return routes(
        "/auth" bind
            routes(
                "/register" bind
                    Method.POST to
                    { request ->
                        val registerRequest = registerRequestLens(request)
                        val res =
                            registerWithEmail(
                                registerRequest.email,
                                registerRequest.password,
                                registerRequest.name,
                            )

                        val token = JwtUtils.generateAccessToken(res.id)
                        println("Generated token: $token for user ${res.name} (${res.id})")
                        Response(Status.OK).with(userResLens of res)
                    },
                "/login" bind
                    Method.POST to
                    { request ->
                        val loginRequest = loginRequestLens(request)
                        loginWithEmail(loginRequest.email, loginRequest.password).let {
                            Response(Status.OK).with(userResLens of it)
                        }
                    },
                "/oauth" bind
                    Method.POST to
                    { request ->
                        val authRequest = authRequestLens(request)
                        val provider = authRequest.provider
                        val providerToken = authRequest.token
                        val authProvider =
                            authProviders[provider] ?: throw RuntimeException("Provider not found")
                        authProvider.authenticate(providerToken)

                        authenticateOrRegister(
                                AuthPayload(
                                    provider = authRequest.provider,
                                    providerUserId = authRequest.providerUserId,
                                    name = authRequest.name,
                                    email = authRequest.email,
                                )
                            )
                            .let { Response(Status.OK).with(userResLens of it) }
                    },
            )
    )
}
