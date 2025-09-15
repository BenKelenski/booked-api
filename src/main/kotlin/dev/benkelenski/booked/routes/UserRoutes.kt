package dev.benkelenski.booked.routes

import dev.benkelenski.booked.constants.ErrorCodes
import dev.benkelenski.booked.constants.ErrorTypes
import dev.benkelenski.booked.domain.apiErrorLens
import dev.benkelenski.booked.domain.responses.ApiError
import dev.benkelenski.booked.domain.userResLens
import dev.benkelenski.booked.middleware.AuthMiddleware
import dev.benkelenski.booked.middleware.authHandler
import dev.benkelenski.booked.services.GetUserById
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.*
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

private val logger = KotlinLogging.logger {}

fun userRoutes(
    getUserById: GetUserById,
    authMiddleware: AuthMiddleware,
): RoutingHttpHandler {

    val getMeHandler = authHandler { userId: Int, _: Request ->
        logger.info { "Getting user info for ID: $userId" }

        getUserById(userId)?.let { user ->
            Response(Status.OK).header("Cache-Control", "no-store").with(Body.userResLens of user)
        }
            ?: Response(Status.UNAUTHORIZED)
                .with(
                    Body.apiErrorLens of
                        ApiError(
                            message = "Unable to get user info",
                            code = ErrorCodes.INSUFFICIENT_PERMISSIONS,
                            type = ErrorTypes.AUTHORIZATION,
                        )
                )
    }

    return routes("/users/me" bind Method.GET to getMeHandler).withFilter(authMiddleware)
}
