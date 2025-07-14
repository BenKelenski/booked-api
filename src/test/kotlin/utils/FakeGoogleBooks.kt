package utils

import dev.benkelenski.booked.clients.googleBooksResponseLens
import dev.benkelenski.booked.clients.projectionLens
import dev.benkelenski.booked.clients.queryLens
import dev.benkelenski.booked.domain.DataBook
import dev.benkelenski.booked.domain.GoogleBooksResponse
import dev.benkelenski.booked.domain.VolumeInfo
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.routing.bind
import org.http4k.routing.routes

private const val DEFAULT_PROJECTION = "lite"

private val fakeGoogleBooksResponse =
    GoogleBooksResponse(
        items =
            listOf(
                DataBook(
                    id = "book1",
                    kind = "books#volume",
                    volumeInfo =
                        VolumeInfo(
                            title = "book one",
                            authors = listOf("author one"),
                            publisher = "book publisher",
                            publishedDate = "1990-12-25",
                            "A really good book!",
                        ),
                )
            )
    )

fun fakeGoogleBooks() =
    routes(
        "/books/v1/volumes" bind
            Method.GET to
            { request ->
                val query = queryLens(request) ?: ""
                val projection = projectionLens(request) ?: DEFAULT_PROJECTION

                val result = fakeGoogleBooksResponse

                Response(Status.OK).with(googleBooksResponseLens of result)
            }
    )
