package utils

import dev.benkelenski.booked.external.google.dto.SearchResultDto
import dev.benkelenski.booked.external.google.dto.VolumeDto
import dev.benkelenski.booked.external.google.dto.VolumeInfoDto
import dev.benkelenski.booked.external.google.searchResultDtoLens
import dev.benkelenski.booked.external.google.volumeDtoLens
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes

fun fakeGoogleBooks() =
    routes(
        "/books/v1/volumes" bind
            Method.GET to
            { request ->
                Response(Status.OK)
                    .with(
                        searchResultDtoLens of
                            SearchResultDto(
                                items =
                                    listOf(
                                        VolumeDto(
                                            id = "book1",
                                            kind = "books#volume",
                                            volumeInfo =
                                                VolumeInfoDto(
                                                    title = "book one",
                                                    authors = listOf("author one"),
                                                    publisher = "book publisher",
                                                    publishedDate = "1990-12-25",
                                                    "A really good book!",
                                                ),
                                        )
                                    )
                            )
                    )
            },
        "/books/v1/volumes/{volume_id}" bind
            Method.GET to
            { request ->
                val googleId = request.path("volume_id")!!

                Response(Status.OK)
                    .with(
                        volumeDtoLens of
                            VolumeDto(
                                id = googleId,
                                kind = "books#volume",
                                volumeInfo =
                                    VolumeInfoDto(
                                        title = "book-$googleId",
                                        authors = listOf("author-$googleId"),
                                        publisher = "publisher-$googleId",
                                        publishedDate = "1990-12-25",
                                        "A really good book!",
                                    ),
                            )
                    )
            },
    )
