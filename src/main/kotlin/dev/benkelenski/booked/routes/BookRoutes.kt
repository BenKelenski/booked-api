package dev.benkelenski.booked.routes

import dev.benkelenski.booked.auth.Verify
import dev.benkelenski.booked.domain.Book
import dev.benkelenski.booked.domain.BookRequest
import dev.benkelenski.booked.domain.DataBook
import dev.benkelenski.booked.services.*
import org.http4k.core.*
import org.http4k.filter.ServerFilters
import org.http4k.format.Moshi.auto
import org.http4k.lens.*
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

val bookIdLens = Path.int().of("book_id")
val booksLens = Body.auto<Array<Book>>().toLens()
val bookLens = Body.auto<Book>().toLens()
val bookRequestLens = Body.auto<BookRequest>().toLens()
val searchQueryLens = Query.string().optional("query")
val dataBooksLens = Body.auto<Array<DataBook>>().toLens()

fun bookRoutes(
    getBook: GetBook,
    getAllBooks: GetAllBooks,
    createBook: CreateBook,
    deleteBook: DeleteBook,
    searchBooks: SearchBooks,
    verify: Verify,
): RoutingHttpHandler {
    val userIdLens = RequestKey.required<String>("userId")
    val authFilter = ServerFilters.BearerAuth(userIdLens, verify)

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
            is BookDeleteResult.Success -> Response(Status.NO_CONTENT)
            is BookDeleteResult.NotFound -> Response(Status.NOT_FOUND)
            is BookDeleteResult.Forbidden -> Response(Status.FORBIDDEN)
            is BookDeleteResult.Failure -> Response(Status.INTERNAL_SERVER_ERROR)
        }
    }

    fun handleSearchGoogleBooks(request: Request): Response {
        val query = searchQueryLens(request)
        println(query)

        return searchBooks(query)?.let { Response(Status.OK).with(dataBooksLens of it) }
            ?: Response(Status.NOT_FOUND)
    }

    return routes(
        "/books" bind
            routes(
                "/" bind Method.GET to ::handleGetAllBooks,
                "/search" bind Method.GET to ::handleSearchGoogleBooks,
                "/$bookIdLens" bind Method.GET to ::handleGetBook,
                "/" bind Method.POST to authFilter.then(::handleCreateBook),
                "/$bookIdLens" bind Method.DELETE to authFilter.then(::handleDeleteBook),
            )
    )
}
