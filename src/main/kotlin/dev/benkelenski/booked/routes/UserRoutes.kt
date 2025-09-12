package dev.benkelenski.booked.routes

import dev.benkelenski.booked.middleware.AuthMiddleware
import dev.benkelenski.booked.services.GetUserById
import dev.benkelenski.booked.utils.parseUserIdHeader
import org.http4k.core.*
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

fun userRoutes(
    getUserById: GetUserById,
    authMiddleware: AuthMiddleware,
): RoutingHttpHandler =
    routes(
        "/users" bind
            authMiddleware.then(
                routes(
                    "/me" bind
                        Method.GET to
                        { request ->
                            val userId =
                                request.parseUserIdHeader()
                                    ?: return@to Response(Status.UNAUTHORIZED)

                            getUserById(userId)?.let { user ->
                                Response(Status.OK)
                                    .header("Cache-Control", "no-store")
                                    .with(userResLens of user)
                            } ?: Response(Status.UNAUTHORIZED)
                        }
                )
            )
    )
