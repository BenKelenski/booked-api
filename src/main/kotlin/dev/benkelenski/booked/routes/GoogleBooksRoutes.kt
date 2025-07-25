package dev.benkelenski.booked.routes

import dev.benkelenski.booked.external.google.dto.VolumeDto
import dev.benkelenski.booked.services.FetchByVolumeId
import dev.benkelenski.booked.services.SearchWithQuery
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import org.http4k.lens.Path
import org.http4k.lens.Query
import org.http4k.lens.string
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

val queryLens = Query.string().required("q")
val googleBooksIdLens = Path.string().of("id")
val googleVolumeLens = Body.auto<VolumeDto>().toLens()
val googleVolumesLens = Body.auto<Array<VolumeDto>>().toLens()

fun googleBooksRoutes(
    searchWithQuery: SearchWithQuery,
    fetchByVolumeId: FetchByVolumeId,
): RoutingHttpHandler =
    routes(
        "/google-books" bind
            routes(
                "/search" bind
                    Method.GET to
                    { request ->
                        searchWithQuery(queryLens(request))?.let {
                            Response(Status.OK).with(googleVolumesLens of it)
                        } ?: Response(Status.NOT_FOUND)
                    },
                "/$googleBooksIdLens" bind
                    Method.GET to
                    { request ->
                        fetchByVolumeId(googleBooksIdLens(request))?.let {
                            Response(Status.OK).with(googleVolumeLens of it)
                        } ?: Response(Status.NOT_FOUND)
                    },
            )
    )
