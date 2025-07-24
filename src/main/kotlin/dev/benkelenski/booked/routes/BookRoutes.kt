package dev.benkelenski.booked.routes

import dev.benkelenski.booked.domain.responses.BookResponse
import dev.benkelenski.booked.external.google.dto.VolumeDto
import dev.benkelenski.booked.middleware.AuthMiddleware
import dev.benkelenski.booked.services.*
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

val bookIdLens = Path.int().of("book_id")
val booksResLens = Body.auto<Array<BookResponse>>().toLens()
val bookResLens = Body.auto<BookResponse>().toLens()
val searchQueryLens = Query.string().optional("query")
val dataBooksLens = Body.auto<Array<VolumeDto>>().toLens()

fun bookRoutes(
    getBookById: GetBookById,
    getAllBooks: GetAllBooksForUser,
    deleteBook: DeleteBook,
    searchBooks: SearchBooks,
    authMiddleware: AuthMiddleware,
): RoutingHttpHandler =
    routes(
        "/books" bind
            authMiddleware.then(
                routes(
                    "/" bind
                        Method.GET to
                        { request ->
                            val userId =
                                request.header("X-User-Id")?.toIntOrNull()
                                    ?: return@to Response(Status.UNAUTHORIZED)

                            getAllBooks(userId).let {
                                Response(Status.OK).with(booksResLens of it.toTypedArray())
                            }
                        },
                    "/search" bind
                        Method.GET to
                        { request ->
                            val userId =
                                request.header("X-User-Id")?.toIntOrNull()
                                    ?: return@to Response(Status.UNAUTHORIZED)
                            val query = searchQueryLens(request)

                            searchBooks(userId, query)?.let {
                                Response(Status.OK).with(dataBooksLens of it)
                            } ?: Response(Status.NOT_FOUND)
                        },
                    "/$bookIdLens" bind
                        Method.GET to
                        { request ->
                            val userId =
                                request.header("X-User-Id")?.toIntOrNull()
                                    ?: return@to Response(Status.UNAUTHORIZED)
                            val bookId = bookIdLens(request)

                            getBookById(userId, bookId)?.let {
                                Response(Status.OK).with(bookResLens of it)
                            } ?: Response(Status.NOT_FOUND)
                        },
                    "/$bookIdLens" bind
                        Method.DELETE to
                        { request ->
                            val userId =
                                request.header("X-User-Id")?.toIntOrNull()
                                    ?: return@to Response(Status.UNAUTHORIZED)
                            val bookId = bookIdLens(request)

                            when (deleteBook(userId, bookId)) {
                                is BookDeleteResult.Success -> Response(Status.NO_CONTENT)
                                is BookDeleteResult.NotFound -> Response(Status.NOT_FOUND)
                                is BookDeleteResult.Forbidden -> Response(Status.FORBIDDEN)
                                is BookDeleteResult.DatabaseError ->
                                    Response(Status.INTERNAL_SERVER_ERROR)
                                        .body("Error occurred trying to delete book.")
                            }
                        },
                )
            )
    )
