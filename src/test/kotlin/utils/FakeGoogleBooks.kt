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
import org.http4k.routing.routes

private const val DEFAULT_PROJECTION = "lite"

private val fakeSearchResultDto =
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

private val fakeVolume =
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

fun fakeGoogleBooks() =
    routes(
        "/books/v1/volumes" bind
            Method.GET to
            { request ->
                Response(Status.OK).with(searchResultDtoLens of fakeSearchResultDto)
            },
        "/books/v1/volumes/{volume_id}" bind
            Method.GET to
            {
                Response(Status.OK).with(volumeDtoLens of fakeVolume)
            },
    )
