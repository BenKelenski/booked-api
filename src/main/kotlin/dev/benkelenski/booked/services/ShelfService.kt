package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.Shelf
import dev.benkelenski.booked.domain.ShelfRequest
import dev.benkelenski.booked.repos.ShelfRepo

/** alias for [ShelfService.getShelfById] */
typealias GetShelfById = (shelfId: Int) -> Shelf?

/** alias for [ShelfService.getAllShelves] */
typealias GetAllShelves = () -> List<Shelf>

/** alias for [ShelfService.createShelf] */
typealias CreateShelf = (userId: Int, shelfRequest: ShelfRequest) -> Shelf?

/** alias for [ShelfService.deleteShelf] */
typealias DeleteShelf = (userId: Int, shelfId: Int) -> ShelfDeleteResult

class ShelfService(private val shelfRepo: ShelfRepo) {

    fun getShelfById(shelfId: Int): Shelf? = shelfRepo.getShelfById(shelfId)

    fun getAllShelves(): List<Shelf> = shelfRepo.getAllShelves()

    fun createShelf(userId: Int, shelfRequest: ShelfRequest): Shelf? =
        shelfRepo.addShelf(userId, shelfRequest.name, shelfRequest.description)

    fun deleteShelf(userId: Int, shelfId: Int): ShelfDeleteResult {
        val shelf = shelfRepo.getShelfById(shelfId) ?: return ShelfDeleteResult.NotFound
        if (shelf.userId != userId) return ShelfDeleteResult.Forbidden
        return if (shelfRepo.deleteShelf(shelfId) == 1) {
            ShelfDeleteResult.Success
        } else {
            ShelfDeleteResult.Failure("Failed to delete $shelf")
        }
    }
}

sealed class ShelfDeleteResult {
    object Success : ShelfDeleteResult()

    object NotFound : ShelfDeleteResult()

    object Forbidden : ShelfDeleteResult()

    data class Failure(val reason: String) : ShelfDeleteResult()
}
