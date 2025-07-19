package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.Shelf
import dev.benkelenski.booked.domain.ShelfRequest
import dev.benkelenski.booked.repos.ShelfRepo
import io.github.oshai.kotlinlogging.KotlinLogging

/** alias for [ShelfService.getShelfById] */
typealias GetShelfById = (userId: Int, shelfId: Int) -> Shelf?

/** alias for [ShelfService.getAllShelves] */
typealias GetAllShelves = (userId: Int) -> List<Shelf>

/** alias for [ShelfService.createShelf] */
typealias CreateShelf = (userId: Int, shelfRequest: ShelfRequest) -> Shelf?

/** alias for [ShelfService.deleteShelf] */
typealias DeleteShelf = (userId: Int, shelfId: Int) -> ShelfDeleteResult

class ShelfService(private val shelfRepo: ShelfRepo) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    fun getShelfById(userId: Int, shelfId: Int): Shelf? = shelfRepo.getShelfById(userId, shelfId)

    fun getAllShelves(userId: Int): List<Shelf> = shelfRepo.getAllShelves(userId)

    fun createShelf(userId: Int, shelfRequest: ShelfRequest): Shelf? =
        shelfRepo.addShelf(userId, shelfRequest.name, shelfRequest.description)

    fun deleteShelf(userId: Int, shelfId: Int): ShelfDeleteResult =
        try {
            val deletedCount = shelfRepo.deleteByIdAndUser(shelfId, userId)

            when {
                deletedCount == 1 -> ShelfDeleteResult.Success
                !shelfRepo.existsById(shelfId) -> ShelfDeleteResult.NotFound
                else -> ShelfDeleteResult.Forbidden
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete shelf $shelfId" }
            ShelfDeleteResult.DatabaseError
        }
}

sealed class ShelfDeleteResult {
    object Success : ShelfDeleteResult()

    object NotFound : ShelfDeleteResult()

    object Forbidden : ShelfDeleteResult()

    object DatabaseError : ShelfDeleteResult()
}
