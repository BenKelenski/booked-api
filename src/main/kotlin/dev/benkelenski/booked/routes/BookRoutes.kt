package dev.benkelenski.booked.routes

import dev.benkelenski.booked.domain.responses.BookResponse
import dev.benkelenski.booked.middleware.AuthMiddleware
import dev.benkelenski.booked.services.BookDeleteResult
import dev.benkelenski.booked.services.DeleteBook
import dev.benkelenski.booked.services.GetAllBooksForUser
import dev.benkelenski.booked.services.GetBookById
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import org.http4k.lens.LensFailure
import org.http4k.lens.Path
import org.http4k.lens.int
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

val bookIdLens = Path.int().of("book_id")
val booksResponseLens = Body.auto<Array<BookResponse>>().toLens()
val bookResponseLens = Body.auto<BookResponse>().toLens()

private val logger = KotlinLogging.logger {}

fun bookRoutes(
    getBookById: GetBookById,
    getAllBooks: GetAllBooksForUser,
    deleteBook: DeleteBook,
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
                                Response(Status.OK).with(booksResponseLens of it.toTypedArray())
                            }
                        },
                    "/$bookIdLens" bind
                        Method.GET to
                        { request ->
                            request.header("X-User-Id")?.toIntOrNull()
                                ?: return@to Response(Status.UNAUTHORIZED)
                            val bookId =
                                try {
                                    bookIdLens(request)
                                } catch (e: LensFailure) {
                                    logger.error(e) { "Missing or invalid book ID." }
                                    return@to Response(Status.BAD_REQUEST)
                                        .body("Missing or invalid book ID.")
                                }

                            getBookById(bookId)?.let {
                                Response(Status.OK).with(bookResponseLens of it)
                            } ?: Response(Status.NOT_FOUND)
                        },
                    "/$bookIdLens" bind
                        Method.DELETE to
                        { request ->
                            val userId =
                                request.header("X-User-Id")?.toIntOrNull()
                                    ?: return@to Response(Status.UNAUTHORIZED)
                            val bookId =
                                try {
                                    bookIdLens(request)
                                } catch (e: LensFailure) {
                                    logger.error(e) { "Missing or invalid book ID." }
                                    return@to Response(Status.BAD_REQUEST)
                                        .body("Missing or invalid book ID.")
                                }

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
