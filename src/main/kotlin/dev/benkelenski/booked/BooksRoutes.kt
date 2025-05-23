package dev.benkelenski.booked

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

fun BookService.toApi(): HttpHandler {
    return routes(
        "/v1/books" bind Method.GET to {
            val result = getBooks().toTypedArray()
            Response(Status.OK)
                .with(booksLens of result)
        },
        "/v1/books/$bookIdLens" bind Method.GET to { request ->
            getBook(bookIdLens(request))
                ?.let { Response(Status.OK).with(bookLens of it) }
                ?: Response(Status.NOT_FOUND)
        },
        "/v1/books" bind Method.POST to { request ->
            createBook(
                bookRequestLens(request)
            )

            Response(Status.CREATED)
        },
        "/v1/books/$bookIdLens" bind Method.DELETE to { request ->
            deleteBook(bookIdLens(request))
                .let { Response(Status.OK) }
        }
    )
}