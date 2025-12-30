package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.ReadingStatus
import dev.benkelenski.booked.domain.requests.CompleteBookRequest
import dev.benkelenski.booked.domain.requests.UpdateBookPatch
import dev.benkelenski.booked.domain.responses.BookResponse
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/** alias for [BookService.findBooksByShelf] */
typealias FindBooksByShelf = (userId: Int, shelves: List<Int>) -> List<BookResponse>

/** alias for [BookService.findBookById] */
typealias FindBookById = (bookId: Int) -> BookResponse?

/** alias for [BookService.updateBook] */
typealias UpdateBook = (userId: Int, bookId: Int, patch: UpdateBookPatch) -> BookUpdateResult

/** alias for [BookService.deleteBook] */
typealias DeleteBook = (userId: Int, bookId: Int) -> BookDeleteResult

/** alias for [BookService.completeBook] */
typealias CompleteBook =
    (userId: Int, bookId: Int, completeBookRequest: CompleteBookRequest) -> BookUpdateResult

class BookService(private val bookRepo: BookRepo, private val shelfRepo: ShelfRepo) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    fun findBooksByShelf(userId: Int, shelves: List<Int>): List<BookResponse> = transaction {
        bookRepo.fetchAllBooksForUser(userId, shelves).map { BookResponse.from(it) }
    }

    fun findBookById(bookId: Int): BookResponse? = transaction {
        bookRepo.fetchById(bookId)?.let { BookResponse.from(it) }
    }

    fun updateBook(userId: Int, bookId: Int, patch: UpdateBookPatch): BookUpdateResult =
        transaction {
            val ownedBook =
                bookRepo.findOwnedMinimal(bookId, userId)
                    ?: return@transaction validateBookExistence(bookId)

            val currentShelfId = ownedBook.shelfId
            val targetShelfId = patch.shelfId ?: currentShelfId

            validateShelfMove(userId, bookId, ownedBook, currentShelfId, targetShelfId)?.let {
                return@transaction it
            }

            val now = Instant.now()

            val rows =
                bookRepo.updateBook(
                    bookId = bookId,
                    moveToShelfId = if (targetShelfId != currentShelfId) targetShelfId else null,
                    currentPage = patch.currentPage,
                    updatedAt = now,
                )
            if (rows == 0) return@transaction BookUpdateResult.NotFound

            val updatedBook =
                bookRepo.fetchById(bookId) ?: return@transaction BookUpdateResult.NotFound

            BookUpdateResult.Success(BookResponse.from(updatedBook))
        }

    fun deleteBook(userId: Int, bookId: Int): BookDeleteResult =
        try {
            transaction {
                val deletedCount = bookRepo.deleteByIdAndUser(userId = userId, bookId = bookId)

                when {
                    deletedCount == 1 -> BookDeleteResult.Success
                    !bookRepo.existsById(bookId) -> BookDeleteResult.NotFound
                    else -> BookDeleteResult.Forbidden
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete book: $bookId" }
            BookDeleteResult.DatabaseError
        }

    fun completeBook(
        userId: Int,
        bookId: Int,
        completeBookRequest: CompleteBookRequest,
    ): BookUpdateResult = transaction {
        val ownedBook =
            bookRepo.findOwnedMinimal(bookId, userId)
                ?: return@transaction validateBookExistence(bookId)

        // Find a user's "FINISHED" shelf
        val finishedShelf =
            shelfRepo.findShelfByStatus(userId, ReadingStatus.FINISHED)
                ?: return@transaction BookUpdateResult.DatabaseError

        validateShelfMove(userId, bookId, ownedBook, ownedBook.shelfId, finishedShelf.id)?.let {
            return@transaction it
        }

        val now = Instant.now()

        val updatedRows =
            bookRepo.completeBook(
                bookId = bookId,
                finishedShelfId = finishedShelf.id,
                rating = completeBookRequest.rating,
                review = completeBookRequest.review,
                finishedAt = now,
                updatedAt = now,
            )

        if (updatedRows == 0) return@transaction BookUpdateResult.DatabaseError

        val updatedBook = bookRepo.fetchById(bookId) ?: return@transaction BookUpdateResult.NotFound

        BookUpdateResult.Success(BookResponse.from(updatedBook))
    }

    private fun validateBookExistence(bookId: Int): BookUpdateResult {
        val exists = bookRepo.existsById(bookId)
        return if (!exists) BookUpdateResult.NotFound else BookUpdateResult.Forbidden
    }

    private fun validateShelfMove(
        userId: Int,
        bookId: Int,
        ownedBook: BookRepo.OwnedBookMinimal,
        currentShelfId: Int,
        targetShelfId: Int,
    ): BookUpdateResult? {
        if (targetShelfId == currentShelfId) return null

        val ownsTargetShelf = shelfRepo.userOwnsShelf(userId, targetShelfId)
        if (!ownsTargetShelf) return BookUpdateResult.Forbidden

        val hasDuplicate =
            bookRepo.existsDuplicateOnShelf(
                targetShelfId = targetShelfId,
                googleId = ownedBook.googleId,
                excludingBookId = bookId,
            )

        return if (hasDuplicate) BookUpdateResult.Conflict else null
    }
}

sealed class BookUpdateResult {
    data class Success(val book: BookResponse) : BookUpdateResult()

    object NotFound : BookUpdateResult()

    object Forbidden : BookUpdateResult()

    object Conflict : BookUpdateResult()

    object DatabaseError : BookUpdateResult()
}

sealed class BookDeleteResult {
    object Success : BookDeleteResult()

    object NotFound : BookDeleteResult()

    object Forbidden : BookDeleteResult()

    object DatabaseError : BookDeleteResult()
}
