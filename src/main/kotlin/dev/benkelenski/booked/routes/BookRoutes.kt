package dev.benkelenski.booked.routes

import dev.benkelenski.booked.models.Book
import dev.benkelenski.booked.models.BookRequest
import dev.benkelenski.booked.services.CreateBook
import dev.benkelenski.booked.services.DeleteBook
import dev.benkelenski.booked.services.GetAllBooks
import dev.benkelenski.booked.services.GetBook
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import org.http4k.lens.Path
import org.http4k.lens.int
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
) = routes(
    "/books" bind Method.GET to {
        getAllBooks()
            .let {
                Response(Status.OK)
                    .with(booksLens of it.toTypedArray())
            }
    },
    "/books/$bookIdLens" bind Method.GET to { request ->
        getBook(bookIdLens(request))
            ?.let { Response(Status.OK).with(bookLens of it) }
            ?: Response(Status.NOT_FOUND)
    },
    "/books" bind Method.POST to { request ->
        createBook(bookRequestLens(request))
            ?.let { Response(Status.CREATED).with(bookLens of it) }
            ?: Response(Status.EXPECTATION_FAILED)
    },
    "/books/$bookIdLens" bind Method.DELETE to { request ->
        deleteBook(bookIdLens(request))
            .let { Response(Status.OK).body("Book successfully deleted: $it") }
    }
)