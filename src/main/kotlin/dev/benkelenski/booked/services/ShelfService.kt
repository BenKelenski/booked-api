package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.Book
import dev.benkelenski.booked.domain.Shelf
import dev.benkelenski.booked.domain.ShelfRequest
import dev.benkelenski.booked.external.google.GoogleBooksClient
import dev.benkelenski.booked.repos.BookRepo
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

/** alias for [ShelfService.getBooksByShelf] */
typealias GetBooksByShelf = (userId: Int, shelfId: Int) -> List<Book>

/** alias for [ShelfService.addBookToShelf] */
typealias AddBookToShelf = (userId: Int, shelfId: Int, googleVolumeId: String) -> ShelfAddBookResult

class ShelfService(
    private val shelfRepo: ShelfRepo,
    private val bookRepo: BookRepo,
    private val googleBooksClient: GoogleBooksClient,
) {

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

    fun getBooksByShelf(userId: Int, shelfId: Int): List<Book> {
        return bookRepo.findAllByShelfAndUser(shelfId, userId)
    }

    fun addBookToShelf(userId: Int, shelfId: Int, googleVolumeId: String): ShelfAddBookResult {
        logger.info { "Adding book $googleVolumeId to shelf $shelfId for user $userId" }

        val shelf =
            shelfRepo.getShelfById(userId, shelfId)
                ?: run {
                    logger.warn { "Shelf $shelfId not found for user $userId" }
                    return ShelfAddBookResult.ShelfNotFound
                }

        if (shelf.userId != userId) {
            logger.warn { "User $userId is not the owner of shelf $shelfId" }
            return ShelfAddBookResult.Forbidden
        }

        val volumeDto =
            googleBooksClient.getVolume(googleVolumeId)
                ?: run {
                    logger.warn { "Book $googleVolumeId not found" }
                    return ShelfAddBookResult.BookNotFound
                }

        val book =
            bookRepo.saveBook(
                userId,
                shelfId,
                volumeDto.id,
                volumeDto.volumeInfo.title,
                volumeDto.volumeInfo.authors,
                volumeDto.volumeInfo.imageLinks?.thumbnail,
            )
                ?: run {
                    logger.warn { "Failed to save book $googleVolumeId to shelf $shelfId" }
                    return ShelfAddBookResult.DatabaseError
                }

        return ShelfAddBookResult.Success(book)
    }
}

sealed class ShelfAddBookResult {
    data class Success(val book: Book) : ShelfAddBookResult()

    object ShelfNotFound : ShelfAddBookResult()

    object BookNotFound : ShelfAddBookResult()

    object Forbidden : ShelfAddBookResult()

    object DatabaseError : ShelfAddBookResult()
}

sealed class ShelfDeleteResult {
    object Success : ShelfDeleteResult()

    object NotFound : ShelfDeleteResult()

    object Forbidden : ShelfDeleteResult()

    object DatabaseError : ShelfDeleteResult()
}
