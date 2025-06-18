package dev.benkelenski.booked.routes

import dev.benkelenski.booked.models.Shelf
import dev.benkelenski.booked.models.ShelfRequest
import dev.benkelenski.booked.services.*
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import org.http4k.lens.Path
import org.http4k.lens.int
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

val shelfIdLens = Path.int().of("shelf_id")
val shelvesLens = Body.auto<Array<Shelf>>().toLens()
val shelfLens = Body.auto<Shelf>().toLens()
val shelfRequestLens = Body.auto<ShelfRequest>().toLens()

fun shelfRoutes(
    getShelf: GetShelf,
    getAllShelves: GetAllShelves,
    createShelf: CreateShelf,
    deleteShelf: DeleteShelf,
    verify: Verify,
): RoutingHttpHandler {

    fun handleGetAllShelves(request: Request): Response {
        return getAllShelves().let { Response(Status.OK).with(shelvesLens of it.toTypedArray()) }
    }

    fun handleGetShelf(request: Request): Response {
        return getShelf(shelfIdLens(request))?.let { Response(Status.OK).with(shelfLens of it) }
            ?: Response(Status.NOT_FOUND)
    }

    fun handleCreateShelf(request: Request): Response {
        return createShelf(shelfRequestLens(request))?.let {
            Response(Status.CREATED).with(shelfLens of it)
        } ?: Response(Status.EXPECTATION_FAILED)
    }

    fun handleDeleteShelf(request: Request): Response {
        return deleteShelf(shelfIdLens(request)).let {
            Response(Status.OK).body("Shelf successfully deleted: $it")
        }
    }

    return routes(
        "/shelves" bind
            routes(
                "/" bind Method.GET to ::handleGetAllShelves,
                "/$shelfIdLens" bind Method.GET to ::handleGetShelf,
                "/" bind Method.POST to ::handleCreateShelf,
                "/$shelfIdLens" bind Method.DELETE to ::handleDeleteShelf,
            )
    )
}
