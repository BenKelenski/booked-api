package dev.benkelenski.booked.routes

// import dev.benkelenski.booked.auth.Verify
import dev.benkelenski.booked.domain.Shelf
import dev.benkelenski.booked.domain.ShelfRequest
import dev.benkelenski.booked.middleware.AuthMiddleware
import dev.benkelenski.booked.services.*
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import org.http4k.lens.Path
import org.http4k.lens.RequestKey
import org.http4k.lens.int
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes

val shelfIdLens = Path.int().of("shelf_id")
val shelvesLens = Body.auto<Array<Shelf>>().toLens()
val shelfLens = Body.auto<Shelf>().toLens()
val shelfRequestLens = Body.auto<ShelfRequest>().toLens()

fun shelfRoutes(
    getShelfById: GetShelfById,
    getAllShelves: GetAllShelves,
    createShelf: CreateShelf,
    deleteShelf: DeleteShelf,
    authMiddleware: AuthMiddleware,
): RoutingHttpHandler {
    val userIdLens = RequestKey.required<Int>("userId")
    //    val authFilter = ServerFilters.BearerAuth(userIdLens, verify)

    fun handleGetAllShelves(request: Request): Response {
        return getAllShelves().let { Response(Status.OK).with(shelvesLens of it.toTypedArray()) }
    }

    fun handleGetShelf(request: Request): Response {
        return getShelfById(shelfIdLens(request))?.let { Response(Status.OK).with(shelfLens of it) }
            ?: Response(Status.NOT_FOUND)
    }

    fun handleCreateShelf(request: Request): Response {
        val userId =
            request.header("X-User-Id")?.toIntOrNull() ?: return Response(Status.UNAUTHORIZED)
        return createShelf(userId, shelfRequestLens(request))?.let {
            Response(Status.CREATED).with(shelfLens of it)
        } ?: Response(Status.EXPECTATION_FAILED)
    }

    fun handleDeleteShelf(request: Request): Response {
        val userId =
            request.header("X-User-Id")?.toIntOrNull() ?: return Response(Status.UNAUTHORIZED)

        return when (deleteShelf(userId, shelfIdLens(request))) {
            is ShelfDeleteResult.Success -> Response(Status.NO_CONTENT)
            is ShelfDeleteResult.NotFound -> Response(Status.NOT_FOUND)
            is ShelfDeleteResult.Forbidden -> Response(Status.FORBIDDEN)
            is ShelfDeleteResult.Failure -> Response(Status.INTERNAL_SERVER_ERROR)
        }
    }

    return routes(
        "/shelves" bind
            routes(
                "/" bind Method.GET to ::handleGetAllShelves,
                "/$shelfIdLens" bind Method.GET to ::handleGetShelf,
                authMiddleware.then(
                    routes(
                        "/" bind Method.POST to ::handleCreateShelf,
                        "/$shelfIdLens" bind Method.DELETE to ::handleDeleteShelf,
                    )
                ),
            )
    )
}
