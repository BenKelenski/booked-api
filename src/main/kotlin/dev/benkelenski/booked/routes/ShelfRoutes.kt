package dev.benkelenski.booked.routes

import dev.benkelenski.booked.domain.Shelf
import dev.benkelenski.booked.domain.ShelfRequest
import dev.benkelenski.booked.domain.requests.BookRequest
import dev.benkelenski.booked.middleware.AuthMiddleware
import dev.benkelenski.booked.services.*
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import org.http4k.lens.Path
import org.http4k.lens.int
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

val shelfIdLens = Path.int().of("shelf_id")
val shelvesLens = Body.auto<Array<Shelf>>().toLens()
val shelfLens = Body.auto<Shelf>().toLens()
val shelfRequestLens = Body.auto<ShelfRequest>().toLens()
val bookRequestLens = Body.auto<BookRequest>().toLens()

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
                                request.header("X-User-Id")?.toIntOrNull()
                                    ?: return@to Response(Status.UNAUTHORIZED)

                            getAllShelves(userId).let {
                                Response(Status.OK).with(shelvesLens of it.toTypedArray())
                            }
                        },
                    "/" bind
                        Method.POST to
                        { request ->
                            val userId =
                                request.header("X-User-Id")?.toIntOrNull()
                                    ?: return@to Response(Status.UNAUTHORIZED)
                            createShelf(userId, shelfRequestLens(request))?.let {
                                Response(Status.CREATED).with(shelfLens of it)
                            } ?: Response(Status.EXPECTATION_FAILED)
                        },
                    "/$shelfIdLens" bind
                        Method.GET to
                        { request ->
                            val userId =
                                request.header("X-User-Id")?.toIntOrNull()
                                    ?: return@to Response(Status.UNAUTHORIZED)

                            getShelfById(userId, shelfIdLens(request))?.let {
                                Response(Status.OK).with(shelfLens of it)
                            } ?: Response(Status.NOT_FOUND)
                        },
                    "/$shelfIdLens" bind
                        Method.DELETE to
                        { request ->
                            val userId =
                                request.header("X-User-Id")?.toIntOrNull()
                                    ?: return@to Response(Status.UNAUTHORIZED)
                            val shelfId = shelfIdLens(request)

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
                                request.header("X-User-Id")?.toIntOrNull()
                                    ?: return@to Response(Status.UNAUTHORIZED)
                            val shelfId = shelfIdLens(request)
                            val books = getBooksByShelf(userId, shelfId)

                            Response(Status.OK).with(booksLens of books.toTypedArray())
                        },
                    "/$shelfIdLens/books" bind
                        Method.POST to
                        { request ->
                            val userId =
                                request.header("X-User-Id")?.toIntOrNull()
                                    ?: return@to Response(Status.UNAUTHORIZED)
                            val shelfId = shelfIdLens(request)
                            val bookRequest = bookRequestLens(request)

                            when (
                                val result = addBookToShelf(userId, shelfId, bookRequest.volumeId)
                            ) {
                                is ShelfAddBookResult.Success ->
                                    Response(Status.OK).with(bookLens of result.book)
                                is ShelfAddBookResult.ShelfNotFound ->
                                    Response(Status.NOT_FOUND).body("Shelf not found.")
                                is ShelfAddBookResult.BookNotFound ->
                                    Response(Status.NOT_FOUND).body("Book not found.")
                                is ShelfAddBookResult.Forbidden ->
                                    Response(Status.FORBIDDEN)
                                        .body("Cannot add books to another user's shelf.")
                                is ShelfAddBookResult.DatabaseError ->
                                    Response(Status.INTERNAL_SERVER_ERROR)
                                        .body("Error occurred trying to add book to shelf.")
                            }

                            Response(Status.CREATED)
                        },
                )
            )
    )
