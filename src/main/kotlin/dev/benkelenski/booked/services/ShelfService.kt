package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.requests.ShelfRequest
import dev.benkelenski.booked.domain.responses.BookResponse
import dev.benkelenski.booked.domain.responses.ShelfResponse
import dev.benkelenski.booked.external.google.GoogleBooksClient
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction

/** alias for [ShelfService.findShelfById] */
typealias FindShelfById = (userId: Int, shelfId: Int) -> ShelfResponse?

/** alias for [ShelfService.findShelvesByUserId] */
typealias FindShelvesByUserId = (userId: Int) -> List<ShelfResponse>

/** alias for [ShelfService.createShelf] */
typealias CreateShelf = (userId: Int, shelfRequest: ShelfRequest) -> ShelfResponse?

/** alias for [ShelfService.deleteShelf] */
typealias DeleteShelf = (userId: Int, shelfId: Int) -> ShelfDeleteResult

/** alias for [ShelfService.addBookToShelf] */
typealias AddBookToShelf = (userId: Int, shelfId: Int, googleVolumeId: String) -> ShelfAddBookResult

class ShelfService(
    private val shelfRepo: ShelfRepo,
    private val bookRepo: BookRepo,
    private val googleBooksClient: GoogleBooksClient,
) {

    companion object {
        private val logger = KotlinLogging.logger {}

        private fun String.secureUrl(): String = replace("http://", "https://")
    }

    fun findShelfById(userId: Int, shelfId: Int): ShelfResponse? = transaction {
        val shelf = shelfRepo.fetchShelfById(userId, shelfId)
        val countOfBooksByShelf = bookRepo.getCountsByShelf(userId)

        shelf?.let { ShelfResponse.from(it, countOfBooksByShelf.getOrDefault(it.id, 0)) }
    }

    fun findShelvesByUserId(userId: Int): List<ShelfResponse> = transaction {
        val shelves = shelfRepo.fetchAllShelvesByUser(userId)
        val countOfBooksByShelf = bookRepo.getCountsByShelf(userId)

        shelves.map { ShelfResponse.from(it, countOfBooksByShelf.getOrDefault(it.id, 0)) }
    }

    fun createShelf(userId: Int, shelfRequest: ShelfRequest): ShelfResponse? = transaction {
        shelfRepo.addShelf(userId, shelfRequest.name, shelfRequest.description)?.let {
            ShelfResponse.from(it, 0)
        }
    }

    fun deleteShelf(userId: Int, shelfId: Int): ShelfDeleteResult =
        try {
            transaction {
                val deletedCount = shelfRepo.deleteByIdAndUser(userId = userId, shelfId = shelfId)

                when {
                    deletedCount == 1 -> ShelfDeleteResult.Success
                    !shelfRepo.existsById(shelfId) -> ShelfDeleteResult.NotFound
                    else -> ShelfDeleteResult.Forbidden
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete shelf $shelfId" }
            ShelfDeleteResult.DatabaseError
        }

    fun findBooksByShelf(userId: Int, shelfId: Int): List<BookResponse> = transaction {
        logger.info { "Getting books for shelf $shelfId for user $userId" }
        bookRepo.findAllByShelfAndUser(shelfId, userId).map { BookResponse.from(it) }
    }

    fun addBookToShelf(userId: Int, shelfId: Int, googleVolumeId: String): ShelfAddBookResult =
        transaction {
            logger.info { "Adding book $googleVolumeId to shelf $shelfId for user $userId" }

            if (bookRepo.existsByGoogleIdAndUser(googleVolumeId, userId)) {
                logger.warn { "User already owns book $googleVolumeId" }
                return@transaction ShelfAddBookResult.Duplicate
            }

            val shelf =
                shelfRepo.fetchShelfById(userId, shelfId)
                    ?: run {
                        logger.warn { "Shelf $shelfId not found for user $userId" }
                        return@transaction ShelfAddBookResult.ShelfNotFound
                    }

            if (shelf.userId != userId) {
                logger.warn { "User $userId is not the owner of shelf $shelfId" }
                return@transaction ShelfAddBookResult.Forbidden
            }

            val volumeDto =
                googleBooksClient.getVolume(googleVolumeId)
                    ?: run {
                        logger.warn { "Book $googleVolumeId not found" }
                        return@transaction ShelfAddBookResult.BookNotFound
                    }

            val book =
                bookRepo.saveBook(
                    userId = userId,
                    shelfId = shelfId,
                    googleId = volumeDto.id,
                    title = volumeDto.volumeInfo.title,
                    authors = volumeDto.volumeInfo.authors,
                    thumbnailUrl = volumeDto.volumeInfo.imageLinks?.thumbnail?.secureUrl(),
                    pageCount = volumeDto.volumeInfo.pageCount,
                )
                    ?: run {
                        logger.warn { "Failed to save book $googleVolumeId to shelf $shelfId" }
                        return@transaction ShelfAddBookResult.DatabaseError
                    }

            ShelfAddBookResult.Success(BookResponse.from(book))
        }
}

sealed class ShelfAddBookResult {
    data class Success(val book: BookResponse) : ShelfAddBookResult()

    object ShelfNotFound : ShelfAddBookResult()

    object BookNotFound : ShelfAddBookResult()

    object Forbidden : ShelfAddBookResult()

    object Duplicate : ShelfAddBookResult()

    object DatabaseError : ShelfAddBookResult()
}

sealed class ShelfDeleteResult {
    object Success : ShelfDeleteResult()

    object NotFound : ShelfDeleteResult()

    object Forbidden : ShelfDeleteResult()

    object DatabaseError : ShelfDeleteResult()
}
