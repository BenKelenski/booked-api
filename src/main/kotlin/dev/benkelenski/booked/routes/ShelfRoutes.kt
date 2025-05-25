package dev.benkelenski.booked.routes

import dev.benkelenski.booked.models.Shelf
import dev.benkelenski.booked.models.ShelfRequest
import dev.benkelenski.booked.services.ShelfService
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import org.http4k.lens.Path
import org.http4k.lens.int
import org.http4k.routing.bind
import org.http4k.routing.routes

val shelfIdLens = Path.int().of("shelf_id")
val shelvesLens = Body.auto<Array<Shelf>>().toLens()
val shelfLens = Body.auto<Shelf>().toLens()
val shelfRequestLens = Body.auto<ShelfRequest>().toLens()

fun shelfRoutes(
    shelfService: ShelfService,
) = routes(
    "/v1/shelves" bind Method.GET to {
        val result = shelfService.getAllShelves().toTypedArray()
        Response(Status.OK)
            .with(shelvesLens of result)
    },
    "/v1/shelves/$shelfIdLens" bind Method.GET to { request ->
        shelfService.getShelf(shelfIdLens(request))
            ?.let { Response(Status.OK).with(shelfLens of it) }
            ?: Response(Status.NOT_FOUND)
    },
    "/v1/shelves" bind Method.POST to { request ->
        shelfService.createShelf(shelfRequestLens(request))
            ?.let { Response(Status.CREATED).with(shelfLens of it) }
            ?: Response(Status.EXPECTATION_FAILED)
    },
    "/v1/shelves/$shelfIdLens" bind Method.DELETE to { request ->
        shelfService.deleteShelf(shelfIdLens(request))
            .let { Response(Status.OK).body("Shelf successfully deleted: $it") }
    }
)
