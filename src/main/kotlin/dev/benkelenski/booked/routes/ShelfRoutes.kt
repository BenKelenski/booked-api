package dev.benkelenski.booked.routes

import dev.benkelenski.booked.constants.ErrorCodes
import dev.benkelenski.booked.constants.ErrorTypes
import dev.benkelenski.booked.domain.*
import dev.benkelenski.booked.domain.responses.ApiError
import dev.benkelenski.booked.middleware.AuthMiddleware
import dev.benkelenski.booked.middleware.authHandler
import dev.benkelenski.booked.services.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.*
import org.http4k.lens.LensFailure
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

private val logger = KotlinLogging.logger {}

fun shelfRoutes(
    findShelfById: FindShelfById,
    findShelvesByUserId: FindShelvesByUserId,
    createShelf: CreateShelf,
    deleteShelf: DeleteShelf,
    addBookToShelf: AddBookToShelf,
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
            try {
                Body.shelfReqLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid shelf request" }
                return@authHandler Response(Status.BAD_REQUEST)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing or invalid shelf request",
                                code = ErrorCodes.MISSING_SHELF_REQUEST,
                                type = ErrorTypes.VALIDATION,
                            )
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

        createShelf(userId, shelfRequest)?.let {
            logger.info { "Created shelf $it for user $userId" }
            Response(Status.CREATED).with(Body.shelfResLens of it)
        } ?: Response(Status.EXPECTATION_FAILED)
    }

    val getShelfHandler = authHandler { userId, request ->
        val shelfId =
            try {
                shelfIdPathLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid shelf ID" }
                return@authHandler Response(Status.BAD_REQUEST)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing or invalid shelf ID",
                                code = ErrorCodes.MISSING_SHELF_ID,
                                type = ErrorTypes.VALIDATION,
                            )
                    )
            }

        findShelfById(userId, shelfId)?.let {
            logger.info { "Found shelf $it for user $userId" }
            Response(Status.OK).with(Body.shelfResLens of it)
        } ?: Response(Status.NOT_FOUND)
    }

    val deleteShelfHandler = authHandler { userId, request ->
        val shelfId =
            try {
                shelfIdPathLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid shelf ID" }
                return@authHandler Response(Status.BAD_REQUEST)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing or invalid shelf ID",
                                code = ErrorCodes.MISSING_SHELF_ID,
                                type = ErrorTypes.VALIDATION,
                            )
                    )
            }

        when (deleteShelf(userId, shelfId)) {
            is ShelfDeleteResult.Success -> {
                logger.info { "Deleted shelf $shelfId for user $userId" }
                Response(Status.NO_CONTENT)
            }
            is ShelfDeleteResult.NotFound ->
                Response(Status.NOT_FOUND)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Shelf does not exist",
                                code = ErrorCodes.SHELF_NOT_FOUND,
                                type = ErrorTypes.NOT_FOUND,
                            )
                    )
            is ShelfDeleteResult.Forbidden ->
                Response(Status.FORBIDDEN)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Cannot delete another user's shelf",
                                code = ErrorCodes.INSUFFICIENT_PERMISSIONS,
                                type = ErrorTypes.AUTHORIZATION,
                            )
                    )

            is ShelfDeleteResult.DatabaseError ->
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

    val addBookToShelfHandler = authHandler { userId, request ->
        val shelfId =
            try {
                shelfIdPathLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid shelf ID" }
                return@authHandler Response(Status.BAD_REQUEST)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing or invalid shelf ID",
                                code = ErrorCodes.MISSING_SHELF_ID,
                                type = ErrorTypes.VALIDATION,
                            )
                    )
            }

        val bookRequest =
            try {
                Body.bookReqLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid book request." }
                return@authHandler Response(Status.BAD_REQUEST)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing or invalid book request",
                                code = ErrorCodes.MISSING_BOOK_REQUEST,
                                type = ErrorTypes.VALIDATION,
                            )
                    )
            }

        if (bookRequest.volumeId.isBlank()) {
            return@authHandler Response(Status.BAD_REQUEST)
                .with(
                    Body.apiErrorLens of
                        ApiError(
                            message = "Google book ID cannot be blank",
                            code = ErrorCodes.BLANK_GOOGLE_VOLUME_ID,
                            type = ErrorTypes.VALIDATION,
                        )
                )
        }

        when (val result = addBookToShelf(userId, shelfId, bookRequest.volumeId)) {
            is ShelfAddBookResult.Success -> {
                logger.info { "Added book ${result.book} to shelf $shelfId for user $userId" }
                Response(Status.OK).with(Body.bookResLens of result.book)
            }

            is ShelfAddBookResult.ShelfNotFound ->
                Response(Status.NOT_FOUND)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Shelf not found",
                                code = ErrorCodes.SHELF_NOT_FOUND,
                                type = ErrorTypes.NOT_FOUND,
                            )
                    )

            is ShelfAddBookResult.BookNotFound ->
                Response(Status.NOT_FOUND)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Book not found",
                                code = ErrorCodes.BOOK_NOT_FOUND,
                                type = ErrorTypes.NOT_FOUND,
                            )
                    )

            is ShelfAddBookResult.Forbidden ->
                Response(Status.FORBIDDEN)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Cannot add books to another user's shelf",
                                code = ErrorCodes.INSUFFICIENT_PERMISSIONS,
                                type = ErrorTypes.AUTHORIZATION,
                            )
                    )

            is ShelfAddBookResult.Duplicate ->
                Response(Status.CONFLICT)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Book already exists on a shelf",
                                code = ErrorCodes.BOOK_ALREADY_EXISTS,
                                type = ErrorTypes.CONFLICT,
                            )
                    )

            is ShelfAddBookResult.DatabaseError ->
                Response(Status.INTERNAL_SERVER_ERROR)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Error occurred trying to add book to shelf",
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
            "/shelves/{shelf_id}/books" bind Method.POST to addBookToShelfHandler,
        )
        .withFilter(authMiddleware)
}
