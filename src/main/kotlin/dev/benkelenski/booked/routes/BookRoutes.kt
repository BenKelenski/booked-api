package dev.benkelenski.booked.routes

import dev.benkelenski.booked.domain.Book
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
val booksLens = Body.auto<Array<Book>>().toLens()
val bookLens = Body.auto<Book>().toLens()
val searchQueryLens = Query.string().optional("query")
val dataBooksLens = Body.auto<Array<VolumeDto>>().toLens()

fun bookRoutes(
    getBookById: GetBookById,
    getAllBooks: GetAllBooks,
    deleteBook: DeleteBook,
    searchBooks: SearchBooks,
    authMiddleware: AuthMiddleware,
): RoutingHttpHandler =
    routes(
        "/books" bind
            routes(
                "/" bind
                    Method.GET to
                    {
                        getAllBooks().let {
                            Response(Status.OK).with(booksLens of it.toTypedArray())
                        }
                    },
                "/search" bind
                    Method.GET to
                    { request ->
                        val query = searchQueryLens(request)

                        searchBooks(query)?.let { Response(Status.OK).with(dataBooksLens of it) }
                            ?: Response(Status.NOT_FOUND)
                    },
                "/$bookIdLens" bind
                    Method.GET to
                    { request ->
                        val bookId = bookIdLens(request)

                        getBookById(bookId)?.let { Response(Status.OK).with(bookLens of it) }
                            ?: Response(Status.NOT_FOUND)
                    },
                authMiddleware.then(
                    routes(
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
                            }
                    )
                ),
            )
    )
