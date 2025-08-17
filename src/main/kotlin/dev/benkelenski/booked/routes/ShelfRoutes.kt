package dev.benkelenski.booked.routes

import dev.benkelenski.booked.domain.requests.BookRequest
import dev.benkelenski.booked.domain.requests.ShelfRequest
import dev.benkelenski.booked.domain.responses.ShelfResponse
import dev.benkelenski.booked.middleware.AuthMiddleware
import dev.benkelenski.booked.services.*
import dev.benkelenski.booked.utils.parseUserIdHeader
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import org.http4k.lens.LensFailure
import org.http4k.lens.Path
import org.http4k.lens.int
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

val shelfIdLens = Path.int().of("shelf_id")
val shelvesResLens = Body.auto<Array<ShelfResponse>>().toLens()
val shelfResLens = Body.auto<ShelfResponse>().toLens()
val shelfRequestLens = Body.auto<ShelfRequest>().toLens()
val bookRequestLens = Body.auto<BookRequest>().toLens()

private val logger = KotlinLogging.logger {}

fun shelfRoutes(
    getShelfById: GetShelfById,
    getAllShelves: GetAllShelves,
    createShelf: CreateShelf,
    deleteShelf: DeleteShelf,
    getBooksByShelf: GetBooksByShelf,
    addBookToShelf: AddBookToShelf,
    authMiddleware: AuthMiddleware,
): RoutingHttpHandler =
    routes(
        "/shelves" bind
            authMiddleware.then(
                routes(
                    "/" bind
                        Method.GET to
                        { request ->
                            val userId =
                                request.parseUserIdHeader()
                                    ?: return@to Response(Status.UNAUTHORIZED)

                            getAllShelves(userId).let {
                                Response(Status.OK).with(shelvesResLens of it.toTypedArray())
                            }
                        },
                    "/" bind
                        Method.POST to
                        { request ->
                            val userId =
                                request.parseUserIdHeader()
                                    ?: return@to Response(Status.UNAUTHORIZED)

                            val shelfRequest =
                                try {
                                    shelfRequestLens(request)
                                } catch (e: LensFailure) {
                                    logger.error(e) { "Missing or invalid shelf request." }
                                    return@to Response(Status.BAD_REQUEST)
                                        .body("Missing or invalid shelf request.")
                                }

                            if (shelfRequest.name.isBlank()) {
                                return@to Response(Status.BAD_REQUEST)
                                    .body("Shelf name cannot be blank.")
                            }

                            createShelf(userId, shelfRequest)?.let {
                                Response(Status.CREATED).with(shelfResLens of it)
                            } ?: Response(Status.EXPECTATION_FAILED)
                        },
                    "/$shelfIdLens" bind
                        Method.GET to
                        { request ->
                            val userId =
                                request.parseUserIdHeader()
                                    ?: return@to Response(Status.UNAUTHORIZED)

                            val shelfId =
                                try {
                                    shelfIdLens(request)
                                } catch (e: LensFailure) {
                                    logger.error(e) { "Missing or invalid shelf ID." }
                                    return@to Response(Status.BAD_REQUEST)
                                        .body("Missing or invalid shelf ID.")
                                }

                            getShelfById(userId, shelfId)?.let {
                                Response(Status.OK).with(shelfResLens of it)
                            } ?: Response(Status.NOT_FOUND)
                        },
                    "/$shelfIdLens" bind
                        Method.DELETE to
                        { request ->
                            val userId =
                                request.parseUserIdHeader()
                                    ?: return@to Response(Status.UNAUTHORIZED)

                            val shelfId =
                                try {
                                    shelfIdLens(request)
                                } catch (e: LensFailure) {
                                    logger.error(e) { "Missing or invalid shelf ID." }
                                    return@to Response(Status.BAD_REQUEST)
                                        .body("Missing or invalid shelf ID.")
                                }

                            when (deleteShelf(userId, shelfId)) {
                                is ShelfDeleteResult.Success -> Response(Status.NO_CONTENT)
                                is ShelfDeleteResult.NotFound -> Response(Status.NOT_FOUND)
                                is ShelfDeleteResult.Forbidden ->
                                    Response(Status.FORBIDDEN)
                                        .body("Cannot delete another user's shelf.")
                                is ShelfDeleteResult.DatabaseError ->
                                    Response(Status.INTERNAL_SERVER_ERROR)
                            }
                        },
                    "/$shelfIdLens/books" bind
                        Method.GET to
                        { request ->
                            val userId =
                                request.parseUserIdHeader()
                                    ?: return@to Response(Status.UNAUTHORIZED)

                            val shelfId =
                                try {
                                    shelfIdLens(request)
                                } catch (e: LensFailure) {
                                    logger.error(e) { "Missing or invalid shelf ID." }
                                    return@to Response(Status.BAD_REQUEST)
                                        .body("Missing or invalid shelf ID.")
                                }

                            val books = getBooksByShelf(userId, shelfId)

                            Response(Status.OK).with(booksResponseLens of books.toTypedArray())
                        },
                    "/$shelfIdLens/books" bind
                        Method.POST to
                        { request ->
                            val userId =
                                request.parseUserIdHeader()
                                    ?: return@to Response(Status.UNAUTHORIZED)

                            val shelfId =
                                try {
                                    shelfIdLens(request)
                                } catch (e: LensFailure) {
                                    logger.error(e) { "Missing or invalid shelf ID." }
                                    return@to Response(Status.BAD_REQUEST)
                                        .body("Missing or invalid shelf ID.")
                                }

                            val bookRequest =
                                try {
                                    bookRequestLens(request)
                                } catch (e: LensFailure) {
                                    logger.error(e) { "Missing or invalid book request." }
                                    return@to Response(Status.BAD_REQUEST)
                                        .body("Missing or invalid book request.")
                                }

                            if (bookRequest.volumeId.isBlank()) {
                                return@to Response(Status.BAD_REQUEST)
                                    .body("Google book ID cannot be blank.")
                            }

                            when (
                                val result = addBookToShelf(userId, shelfId, bookRequest.volumeId)
                            ) {
                                is ShelfAddBookResult.Success ->
                                    Response(Status.OK).with(bookResponseLens of result.book)
                                is ShelfAddBookResult.ShelfNotFound ->
                                    Response(Status.NOT_FOUND).body("Shelf not found.")
                                is ShelfAddBookResult.BookNotFound ->
                                    Response(Status.NOT_FOUND)
                                        .body(
                                            "No Google book found with ID=${bookRequest.volumeId}"
                                        )
                                is ShelfAddBookResult.Forbidden ->
                                    Response(Status.FORBIDDEN)
                                        .body("Cannot add books to another user's shelf.")
                                is ShelfAddBookResult.Duplicate ->
                                    Response(Status.CONFLICT)
                                        .body("Book already exists on a shelf.")
                                is ShelfAddBookResult.DatabaseError ->
                                    Response(Status.INTERNAL_SERVER_ERROR)
                                        .body("Error occurred trying to add book to shelf.")
                            }
                        },
                )
            )
    )
