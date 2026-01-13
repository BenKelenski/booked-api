package dev.benkelenski.booked.routes

import dev.benkelenski.booked.constants.ErrorCodes
import dev.benkelenski.booked.constants.ErrorTypes
import dev.benkelenski.booked.domain.*
import dev.benkelenski.booked.domain.responses.ApiError
import dev.benkelenski.booked.middleware.AuthMiddleware
import dev.benkelenski.booked.middleware.authHandler
import dev.benkelenski.booked.services.*
import dev.benkelenski.booked.utils.createValidationErrorResponse
import dev.benkelenski.booked.utils.extractLensOrNull
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.*
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

private val logger = KotlinLogging.logger {}

fun shelfRoutes(
    findShelfById: FindShelfById,
    findShelvesByUserId: FindShelvesByUserId,
    createShelf: CreateShelf,
    deleteShelf: DeleteShelf,
    authMiddleware: AuthMiddleware,
): RoutingHttpHandler {
    val getShelvesHandler = authHandler { userId, _ ->
        findShelvesByUserId(userId).let {
            logger.info { "Found ${it.size} shelves for user $userId" }
            Response(Status.OK).with(Body.shelvesResLens of it.toTypedArray())
        }
    }

    val createShelfHandler = authHandler { userId, request ->
        val shelfRequest =
            extractLensOrNull(
                request = request,
                lens = Body.shelfReqLens,
                errorMessage = "Missing or invalid shelf request",
            )
                ?: run {
                    logger.warn { "Missing or invalid shelf request" }
                    return@authHandler createValidationErrorResponse(
                        message = "Missing or invalid shelf request",
                        code = ErrorCodes.MISSING_SHELF_ID,
                    )
                }

        if (shelfRequest.name.isBlank()) {
            return@authHandler Response(Status.BAD_REQUEST)
                .with(
                    Body.apiErrorLens of
                        ApiError(
                            message = "Shelf name cannot be blank",
                            code = ErrorCodes.BLANK_SHELF_NAME,
                            type = ErrorTypes.VALIDATION,
                        )
                )
        }

        when (val result = createShelf(userId, shelfRequest)) {
            is CreateShelfResult.Success -> {
                Response(Status.CREATED).with(Body.shelfResLens of result.shelf)
            }
            is CreateShelfResult.DatabaseError -> {
                Response(Status.INTERNAL_SERVER_ERROR)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Error occurred trying to create shelf",
                                code = ErrorCodes.INTERNAL_SERVER_ERROR,
                                type = ErrorTypes.SYSTEM,
                            )
                    )
            }
        }
    }

    val getShelfHandler = authHandler { userId, request ->
        val shelfId =
            extractLensOrNull(
                request = request,
                lens = shelfIdPathLens,
                errorMessage = "Missing or invalid shelf ID",
            )
                ?: run {
                    logger.warn { "Missing or invalid shelf ID" }
                    return@authHandler createValidationErrorResponse(
                        message = "Missing or invalid shelf ID",
                        code = ErrorCodes.MISSING_SHELF_ID,
                    )
                }

        findShelfById(userId, shelfId)?.let {
            logger.info { "Found shelf $it for user $userId" }
            Response(Status.OK).with(Body.shelfResLens of it)
        } ?: Response(Status.NOT_FOUND)
    }

    val deleteShelfHandler = authHandler { userId, request ->
        val shelfId =
            extractLensOrNull(
                request = request,
                lens = shelfIdPathLens,
                errorMessage = "Missing or invalid shelf ID",
            )
                ?: run {
                    logger.warn { "Missing or invalid shelf ID" }
                    return@authHandler createValidationErrorResponse(
                        message = "Missing or invalid shelf ID",
                        code = ErrorCodes.MISSING_SHELF_ID,
                    )
                }

        when (deleteShelf(userId, shelfId)) {
            is DeleteShelfResult.Success -> {
                logger.info { "Deleted shelf $shelfId for user $userId" }
                Response(Status.NO_CONTENT)
            }
            is DeleteShelfResult.NotFound ->
                Response(Status.NOT_FOUND)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Shelf does not exist",
                                code = ErrorCodes.SHELF_NOT_FOUND,
                                type = ErrorTypes.NOT_FOUND,
                            )
                    )
            is DeleteShelfResult.Forbidden ->
                Response(Status.FORBIDDEN)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Cannot delete another user's shelf",
                                code = ErrorCodes.INSUFFICIENT_PERMISSIONS,
                                type = ErrorTypes.AUTHORIZATION,
                            )
                    )

            is DeleteShelfResult.DatabaseError ->
                Response(Status.INTERNAL_SERVER_ERROR)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Internal Server Error",
                                code = ErrorCodes.INTERNAL_SERVER_ERROR,
                                type = ErrorTypes.SYSTEM,
                            )
                    )
        }
    }

    return routes(
            "/shelves" bind Method.GET to getShelvesHandler,
            "/shelves" bind Method.POST to createShelfHandler,
            "/shelves/{shelf_id}" bind Method.GET to getShelfHandler,
            "/shelves/{shelf_id}" bind Method.DELETE to deleteShelfHandler,
        )
        .withFilter(authMiddleware)
}
