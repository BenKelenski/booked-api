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
import org.http4k.lens.LensFailure
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

private val logger = KotlinLogging.logger {}

fun bookRoutes(
    findBookById: FindBookById,
    findBooksByUser: FindBooksByUser,
    updateBook: UpdateBook,
    deleteBook: DeleteBook,
    completeBook: CompleteBook,
    authMiddleware: AuthMiddleware,
): RoutingHttpHandler {

    val getBooksHandler = authHandler { userId, request ->
        logger.info { "Received book retrieval request for user: $userId" }
        findBooksByUser(userId).let {
            Response(Status.OK).with(Body.booksResLens of it.toTypedArray())
        }
    }

    val getBookHandler = authHandler { userId, request ->
        val bookId =
            try {
                bookIdLens(request)
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

    val patchBookHandler = authHandler { userId, request ->
        val bookId =
            try {
                bookIdLens(request)
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

        val patch =
            try {
                Body.bookPatchLens(request)
            } catch (e: LensFailure) {
                logger.error(e) { "Missing or invalid book update request" }
                return@authHandler Response(Status.BAD_REQUEST)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing or invalid book update request",
                                code = ErrorCodes.INVALID_BOOK_UPDATE_REQUEST,
                                type = ErrorTypes.VALIDATION,
                            )
                    )
            }

        if (patch.isEmpty()) {
            logger.error { "Patch cannot be empty" }
            return@authHandler Response(Status.BAD_REQUEST)
                .with(
                    Body.apiErrorLens of
                        ApiError(
                            message = "All book patch fields cannot be empty",
                            code = ErrorCodes.INVALID_BOOK_UPDATE_REQUEST,
                            type = ErrorTypes.VALIDATION,
                        )
                )
        }

        logger.info { "Received book patch request: $patch for book: $bookId" }

        when (val res = updateBook(userId, bookId, patch)) {
            is BookUpdateResult.Success -> {
                logger.info { "Successfully updated book: $bookId" }
                Response(Status.OK).with(Body.bookResLens of res.book)
            }

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
            is BookUpdateResult.Forbidden ->
                Response(Status.FORBIDDEN)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Insufficient permissions to update book",
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
                                message = "Error occurred trying to update book",
                                code = ErrorCodes.INTERNAL_SERVER_ERROR,
                                type = ErrorTypes.SYSTEM,
                            )
                    )
        }
    }

    val deleteBookHandler = authHandler { userId, request ->
        val bookId =
            try {
                bookIdLens(request)
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

    val completeBookHandler = authHandler { userId, request ->
        val bookId =
            extractLensOrNull(
                request = request,
                lens = bookIdLens,
                errorMessage = "Missing or invalid book ID",
            )
                ?: return@authHandler createValidationErrorResponse(
                    "Missing or invalid book ID",
                    ErrorCodes.MISSING_BOOK_ID,
                )

        val completeRequest =
            extractLensOrNull(
                request = request,
                lens = Body.completeBookLens,
                errorMessage = "Missing or invalid book completed request",
            )
                ?: return@authHandler createValidationErrorResponse(
                    "Missing or invalid book completed request",
                    ErrorCodes.INVALID_BOOK_COMPLETED_REQUEST,
                )

        val validationResult: List<String> = completeRequest.validate()
        if (validationResult.isNotEmpty()) {
            return@authHandler createValidationErrorResponse(
                message = validationResult.joinToString(", "),
                ErrorCodes.INVALID_BOOK_COMPLETED_REQUEST,
            )
        }

        logger.info { "Received book completion request: $completeRequest for $bookId" }

        when (val result = completeBook(userId, bookId, completeRequest)) {
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
            is BookUpdateResult.Forbidden ->
                Response(Status.FORBIDDEN)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Insufficient permissions to complete book",
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
                                message = "Error occurred trying to complete book",
                                code = ErrorCodes.INTERNAL_SERVER_ERROR,
                                type = ErrorTypes.SYSTEM,
                            )
                    )
        }
    }

    return routes(
            "/books" bind Method.GET to getBooksHandler,
            "/books/{book_id}" bind Method.GET to getBookHandler,
            "/books/{book_id}" bind Method.PATCH to patchBookHandler,
            "/books/{book_id}" bind Method.DELETE to deleteBookHandler,
            "/books/{book_id}/complete" bind Method.POST to completeBookHandler,
        )
        .withFilter(authMiddleware)
}
