package dev.benkelenski.booked.routes

import dev.benkelenski.booked.constants.ErrorCodes
import dev.benkelenski.booked.constants.ErrorTypes
import dev.benkelenski.booked.domain.*
import dev.benkelenski.booked.domain.requests.CompleteBookRequest
import dev.benkelenski.booked.domain.responses.ApiError
import dev.benkelenski.booked.middleware.AuthMiddleware
import dev.benkelenski.booked.middleware.authHandler
import dev.benkelenski.booked.services.*
import dev.benkelenski.booked.utils.createValidationErrorResponse
import dev.benkelenski.booked.utils.extractLensOrNull
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.*
import org.http4k.lens.LensFailure
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

private val logger = KotlinLogging.logger {}

fun bookRoutes(
    findBookById: FindBookById,
    findBooksByShelf: FindBooksByShelf,
    moveBook: MoveBook,
    updateBookProgress: UpdateBookProgress,
    deleteBook: DeleteBook,
    completeBook: CompleteBook,
    authMiddleware: AuthMiddleware,
): RoutingHttpHandler {

    val getBooksHandler = authHandler { userId, request ->
        logger.info { "Received book retrieval request for user: $userId" }

        val shelves = shelfIdQueryLens(request)

        findBooksByShelf(userId, shelves).let {
            Response(Status.OK).with(Body.booksResLens of it.toTypedArray())
        }
    }

    val getBookHandler = authHandler { userId, request ->
        val bookId =
            try {
                bookIdPathLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid book ID." }
                return@authHandler Response(Status.BAD_REQUEST)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                "Missing or invalid book ID",
                                code = ErrorCodes.MISSING_BOOK_ID,
                                type = ErrorTypes.VALIDATION,
                            )
                    )
            }

        logger.info { "Received book retrieval request for book: $bookId" }

        findBookById(bookId)?.let { Response(Status.OK).with(Body.bookResLens of it) }
            ?: Response(Status.NOT_FOUND)
    }

    val deleteBookHandler = authHandler { userId, request ->
        val bookId =
            try {
                bookIdPathLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid book ID" }
                return@authHandler Response(Status.BAD_REQUEST).body("Missing or invalid book ID")
            }

        logger.info { "Received book deletion request for book: $bookId" }

        when (deleteBook(userId, bookId)) {
            is BookDeleteResult.Success -> Response(Status.NO_CONTENT)
            is BookDeleteResult.NotFound -> Response(Status.NOT_FOUND)
            is BookDeleteResult.Forbidden -> Response(Status.FORBIDDEN)
            is BookDeleteResult.DatabaseError ->
                Response(Status.INTERNAL_SERVER_ERROR).body("Error occurred trying to delete book")
        }
    }

    val moveBookHandler = authHandler { userId, request ->
        val bookId =
            try {
                bookIdPathLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid book ID" }
                return@authHandler Response(Status.BAD_REQUEST)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing or invalid book ID",
                                code = ErrorCodes.MISSING_BOOK_ID,
                                type = ErrorTypes.VALIDATION,
                            )
                    )
            }

        val moveBookRequest =
            try {
                Body.moveBookReqLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid move book request" }
                return@authHandler Response(Status.BAD_REQUEST)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing or invalid move book request",
                                code = ErrorCodes.INVALID_BOOK_MOVE_REQUEST,
                                type = ErrorTypes.VALIDATION,
                            )
                    )
            }

        logger.info {
            "Received book move request for book: $bookId to shelf: ${moveBookRequest.shelfId}"
        }

        when (val result = moveBook(userId, bookId, moveBookRequest.shelfId)) {
            is BookUpdateResult.Success -> Response(Status.OK).with(Body.bookResLens of result.book)
            is BookUpdateResult.NotFound ->
                Response(Status.NOT_FOUND)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Book not found",
                                code = ErrorCodes.BOOK_NOT_FOUND,
                                type = ErrorTypes.NOT_FOUND,
                            )
                    )
            is BookUpdateResult.ShelfNotFound ->
                Response(Status.NOT_FOUND)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Shelf not found",
                                code = ErrorCodes.SHELF_NOT_FOUND,
                                type = ErrorTypes.NOT_FOUND,
                            )
                    )
            is BookUpdateResult.Forbidden ->
                Response(Status.FORBIDDEN)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Insufficient permissions to move book",
                                code = ErrorCodes.INSUFFICIENT_PERMISSIONS,
                                type = ErrorTypes.AUTHORIZATION,
                            )
                    )
            is BookUpdateResult.Conflict ->
                Response(Status.CONFLICT)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Book already exists on destination shelf",
                                code = ErrorCodes.BOOK_ALREADY_EXISTS,
                                type = ErrorTypes.CONFLICT,
                            )
                    )
            is BookUpdateResult.DatabaseError ->
                Response(Status.INTERNAL_SERVER_ERROR)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Error occurred trying to move book",
                                code = ErrorCodes.INTERNAL_SERVER_ERROR,
                                type = ErrorTypes.SYSTEM,
                            )
                    )
        }
    }

    val updateBookProgressHandler = authHandler { userId, request ->
        val bookId =
            try {
                bookIdPathLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid book ID" }
                return@authHandler Response(Status.BAD_REQUEST)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing or invalid book ID",
                                code = ErrorCodes.MISSING_BOOK_ID,
                                type = ErrorTypes.VALIDATION,
                            )
                    )
            }

        val updateBookProgReq =
            try {
                Body.updateBookProgressReqLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid update book progress request" }
                return@authHandler Response(Status.BAD_REQUEST)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing or invalid update book progress request",
                                code = ErrorCodes.INVALID_BOOK_PROGRESS_REQUEST,
                                type = ErrorTypes.VALIDATION,
                            )
                    )
            }

        logger.info {
            "Received book progress update request for book: $bookId to page: ${updateBookProgReq.latestPage}"
        }

        when (val result = updateBookProgress(userId, bookId, updateBookProgReq.latestPage)) {
            is BookUpdateResult.Success -> Response(Status.OK).with(Body.bookResLens of result.book)
            is BookUpdateResult.NotFound ->
                Response(Status.NOT_FOUND)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Book not found",
                                code = ErrorCodes.BOOK_NOT_FOUND,
                                type = ErrorTypes.NOT_FOUND,
                            )
                    )
            is BookUpdateResult.ShelfNotFound ->
                Response(Status.NOT_FOUND)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Shelf not found",
                                code = ErrorCodes.SHELF_NOT_FOUND,
                                type = ErrorTypes.NOT_FOUND,
                            )
                    )
            is BookUpdateResult.Forbidden ->
                Response(Status.FORBIDDEN)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Cannot update progress for book.",
                                code = ErrorCodes.BOOK_NOT_IN_PROGRESS,
                                type = ErrorTypes.BUSINESS_RULE,
                            )
                    )
            is BookUpdateResult.Conflict,
            BookUpdateResult.DatabaseError ->
                Response(Status.INTERNAL_SERVER_ERROR)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Error occurred trying to update book progress.",
                                code = ErrorCodes.INTERNAL_SERVER_ERROR,
                                type = ErrorTypes.SERVICE,
                            )
                    )
        }
    }

    val completeBookHandler = authHandler { userId, request ->
        val bookId =
            extractLensOrNull(
                request = request,
                lens = bookIdPathLens,
                errorMessage = "Missing or invalid book ID",
            )
                ?: run {
                    logger.warn { "Missing or invalid book ID" }
                    return@authHandler createValidationErrorResponse(
                        "Missing or invalid book ID",
                        ErrorCodes.MISSING_BOOK_ID,
                    )
                }

        val completeRequest: CompleteBookRequest =
            extractLensOrNull(
                request = request,
                lens = Body.completeBookLens,
                errorMessage = "Missing or invalid book completed request",
            )
                ?: run {
                    logger.warn { "Missing or invalid book completed request" }
                    return@authHandler createValidationErrorResponse(
                        "Missing or invalid book completed request",
                        ErrorCodes.INVALID_BOOK_COMPLETED_REQUEST,
                    )
                }

        val validationResult: List<String> = completeRequest.validate()
        if (validationResult.isNotEmpty()) {
            logger.warn { "Book validation failed: $validationResult" }
            return@authHandler createValidationErrorResponse(
                message = validationResult.joinToString(", "),
                ErrorCodes.INVALID_BOOK_COMPLETED_REQUEST,
            )
        }

        logger.info { "Received book completion request: $completeRequest for $bookId" }

        when (val result = completeBook(userId, bookId, completeRequest)) {
            is BookUpdateResult.Success -> {
                logger.info { "Successfully completed book: $bookId" }
                Response(Status.OK).with(Body.bookResLens of result.book)
            }
            is BookUpdateResult.NotFound -> {
                logger.info { "Book not found" }
                Response(Status.NOT_FOUND)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Book not found",
                                code = ErrorCodes.BOOK_NOT_FOUND,
                                type = ErrorTypes.NOT_FOUND,
                            )
                    )
            }
            is BookUpdateResult.ShelfNotFound -> {
                logger.info { "Shelf not found" }
                Response(Status.NOT_FOUND)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Shelf not found",
                                code = ErrorCodes.SHELF_NOT_FOUND,
                                type = ErrorTypes.NOT_FOUND,
                            )
                    )
            }
            is BookUpdateResult.Forbidden -> {
                logger.info { "Insufficient permissions to complete book" }
                Response(Status.FORBIDDEN)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Insufficient permissions to complete book",
                                code = ErrorCodes.INSUFFICIENT_PERMISSIONS,
                                type = ErrorTypes.AUTHORIZATION,
                            )
                    )
            }
            is BookUpdateResult.Conflict -> {
                logger.info { "Book already exists on destination shelf" }
                Response(Status.CONFLICT)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Book already exists on destination shelf",
                                code = ErrorCodes.BOOK_ALREADY_EXISTS,
                                type = ErrorTypes.CONFLICT,
                            )
                    )
            }
            is BookUpdateResult.DatabaseError -> {
                logger.error { "Error occurred trying to complete book" }
                Response(Status.INTERNAL_SERVER_ERROR)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Error occurred trying to complete book",
                                code = ErrorCodes.INTERNAL_SERVER_ERROR,
                                type = ErrorTypes.SYSTEM,
                            )
                    )
            }
        }
    }

    return routes(
            "/books" bind Method.GET to getBooksHandler,
            "/books/{book_id}" bind Method.GET to getBookHandler,
            "/books/{book_id}" bind Method.DELETE to deleteBookHandler,
            "/books/{book_id}/move" bind Method.POST to moveBookHandler,
            "/books/{book_id}/progress" bind Method.PATCH to updateBookProgressHandler,
            "/books/{book_id}/complete" bind Method.POST to completeBookHandler,
        )
        .withFilter(authMiddleware)
}
