package dev.benkelenski.booked.routes

import dev.benkelenski.booked.models.Book
import dev.benkelenski.booked.models.BookRequest
import dev.benkelenski.booked.services.*
import org.http4k.core.*
import org.http4k.filter.ServerFilters
import org.http4k.format.Moshi.auto
import org.http4k.lens.Path
import org.http4k.lens.RequestKey
import org.http4k.lens.int
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

val bookIdLens = Path.int().of("book_id")
val booksLens = Body.auto<Array<Book>>().toLens()
val bookLens = Body.auto<Book>().toLens()
val bookRequestLens = Body.auto<BookRequest>().toLens()

fun bookRoutes(
    getBook: GetBook,
    getAllBooks: GetAllBooks,
    createBook: CreateBook,
    deleteBook: DeleteBook,
    verify: Verify,
): RoutingHttpHandler {
    val userIdLens = RequestKey.required<String>("userId")
    val authFiler = ServerFilters.BearerAuth(userIdLens, verify)

    fun handleGetAllBooks(request: Request): Response {
        return getAllBooks().let { Response(Status.OK).with(booksLens of it.toTypedArray()) }
    }

    fun handleGetBook(request: Request): Response {
        return getBook(bookIdLens(request))?.let { Response(Status.OK).with(bookLens of it) }
            ?: Response(Status.NOT_FOUND)
    }

    fun handleCreateBook(request: Request): Response {
        val userId = userIdLens(request)
        return createBook(userId, bookRequestLens(request))?.let {
            Response(Status.CREATED).with(bookLens of it)
        } ?: Response(Status.EXPECTATION_FAILED)
    }

    fun handleDeleteBook(request: Request): Response {
        val userId = userIdLens(request)

        return when (deleteBook(userId, bookIdLens(request))) {
            is DeleteResult.Success -> Response(Status.NO_CONTENT)
            is DeleteResult.NotFound -> Response(Status.NOT_FOUND)
            is DeleteResult.Forbidden -> Response(Status.FORBIDDEN)
            is DeleteResult.Failure -> Response(Status.INTERNAL_SERVER_ERROR)
        }
    }

    return routes(
        "/books" bind Method.GET to ::handleGetAllBooks,
        "/books/$bookIdLens" bind Method.GET to ::handleGetBook,
        "/books" bind Method.POST to authFiler.then(::handleCreateBook),
        "/books/$bookIdLens" bind Method.DELETE to authFiler.then(::handleDeleteBook),
    )
}
