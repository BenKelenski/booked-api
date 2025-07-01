package dev.benkelenski.booked.services

import dev.benkelenski.booked.models.Shelf
import dev.benkelenski.booked.models.ShelfRequest
import dev.benkelenski.booked.repos.ShelfRepo

/** alias for [ShelfService.getShelf] */
typealias GetShelf = (shelfId: Int) -> Shelf?

/** alias for [ShelfService.getAllShelves] */
typealias GetAllShelves = () -> List<Shelf>

/** alias for [ShelfService.createShelf] */
typealias CreateShelf = (userId: String, shelfRequest: ShelfRequest) -> Shelf?

/** alias for [ShelfService.deleteShelf] */
typealias DeleteShelf = (userId: String, shelfId: Int) -> ShelfDeleteResult

class ShelfService(private val shelfRepo: ShelfRepo) {

    fun getShelf(shelfId: Int): Shelf? = shelfRepo.getShelfById(shelfId)

    fun getAllShelves(): List<Shelf> = shelfRepo.getAllShelves()

    fun createShelf(userId: String, shelfRequest: ShelfRequest): Shelf? =
        shelfRepo.addShelf(userId, shelfRequest.name, shelfRequest.description)

    fun deleteShelf(userId: String, shelfId: Int): ShelfDeleteResult {
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
