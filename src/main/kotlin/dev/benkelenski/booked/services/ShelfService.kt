package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.requests.ShelfRequest
import dev.benkelenski.booked.domain.responses.ShelfResponse
import dev.benkelenski.booked.domain.responses.toResponse
import dev.benkelenski.booked.repos.ShelfRepo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction

/** alias for [ShelfService.findShelfById] */
typealias FindShelfById = (userId: Int, shelfId: Int) -> ShelfResponse?

/** alias for [ShelfService.findShelvesByUserId] */
typealias FindShelvesByUserId = (userId: Int) -> List<ShelfResponse>

/** alias for [ShelfService.createShelf] */
typealias CreateShelf = (userId: Int, shelfRequest: ShelfRequest) -> CreateShelfResult

/** alias for [ShelfService.deleteShelf] */
typealias DeleteShelf = (userId: Int, shelfId: Int) -> DeleteShelfResult

class ShelfService(
    private val shelfRepo: ShelfRepo,
) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    fun findShelfById(userId: Int, shelfId: Int): ShelfResponse? = transaction {
        shelfRepo.fetchShelvesWithBookCounts(userId, shelfId).firstOrNull()?.toResponse()
    }

    fun findShelvesByUserId(userId: Int): List<ShelfResponse> = transaction {
        shelfRepo.fetchShelvesWithBookCounts(userId).map { shelf -> shelf.toResponse() }
    }

    fun createShelf(userId: Int, shelfRequest: ShelfRequest): CreateShelfResult = transaction {
        val newShelf =
            shelfRepo.addShelf(userId, shelfRequest.name, shelfRequest.description)
                ?: return@transaction CreateShelfResult.DatabaseError

        CreateShelfResult.Success(newShelf.toResponse())
    }

    fun deleteShelf(userId: Int, shelfId: Int): DeleteShelfResult =
        try {
            transaction {
                val deletedCount = shelfRepo.deleteByIdAndUser(userId = userId, shelfId = shelfId)

                when {
                    deletedCount == 1 -> DeleteShelfResult.Success
                    !shelfRepo.existsById(shelfId) -> DeleteShelfResult.NotFound
                    else -> DeleteShelfResult.Forbidden
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete shelf $shelfId" }
            DeleteShelfResult.DatabaseError
        }
}

sealed class CreateShelfResult {
    data class Success(val shelf: ShelfResponse) : CreateShelfResult()

    object DatabaseError : CreateShelfResult()
}

sealed class DeleteShelfResult {
    object Success : DeleteShelfResult()

    object NotFound : DeleteShelfResult()

    object Forbidden : DeleteShelfResult()

    object DatabaseError : DeleteShelfResult()
}
