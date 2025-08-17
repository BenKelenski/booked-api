package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.ReadingStatus
import dev.benkelenski.booked.domain.requests.UpdateBookPatch
import dev.benkelenski.booked.domain.responses.BookResponse
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import org.jetbrains.exposed.sql.transactions.transaction

/** alias for [BookService.getAllBooksForUser] */
typealias GetAllBooksForUser = (userId: Int) -> List<BookResponse>

/** alias for [BookService.getBookById] */
typealias GetBookById = (bookId: Int) -> BookResponse?

/** alias for [BookService.updateBook] */
typealias UpdateBook = (userId: Int, bookId: Int, patch: UpdateBookPatch) -> BookUpdateResult

/** alias for [BookService.deleteBook] */
typealias DeleteBook = (userId: Int, bookId: Int) -> BookDeleteResult

class BookService(private val bookRepo: BookRepo, private val shelfRepo: ShelfRepo) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    fun getAllBooksForUser(userId: Int): List<BookResponse> = transaction {
        bookRepo.fetchAllBooksByUser(userId).map { BookResponse.from(it) }
    }

    fun getBookById(bookId: Int): BookResponse? = transaction {
        bookRepo.fetchById(bookId)?.let { BookResponse.from(it) }
    }

    fun updateBook(userId: Int, bookId: Int, patch: UpdateBookPatch): BookUpdateResult =
        transaction {
            patch.progressPercent?.let { p ->
                if (p !in 0..100)
                    return@transaction BookUpdateResult.ValidationError(
                        "progressPercent must be 0..100"
                    )
            }

            val ownedBook = bookRepo.findOwnedMinimal(bookId, userId)
            if (ownedBook == null) {
                val exists = bookRepo.existsById(bookId)
                if (!exists) return@transaction BookUpdateResult.NotFound
                else return@transaction BookUpdateResult.Forbidden
            }
            val currentShelfId = ownedBook.shelfId
            val targetShelfId = patch.newShelfId ?: currentShelfId
            if (targetShelfId != currentShelfId) {
                val ownsTargetShelf = shelfRepo.userOwnsShelf(userId, targetShelfId)
                if (!ownsTargetShelf) return@transaction BookUpdateResult.Forbidden
                val dup =
                    bookRepo.existsDuplicateOnShelf(
                        targetShelfId = targetShelfId,
                        googleId = ownedBook.googleId,
                        excludingBookId = bookId,
                    )
                if (dup) return@transaction BookUpdateResult.Conflict
            }

            val now = Instant.now()
            val finishedAt =
                when (patch.status) {
                    ReadingStatus.FINISHED -> now
                    else -> null
                }
            val updatedAt = now // if you store updated_at from app side
            val rows =
                bookRepo.applyPatch(
                    bookId = bookId,
                    moveToShelfId = if (targetShelfId != currentShelfId) targetShelfId else null,
                    progressPercent = patch.progressPercent,
                    status = patch.status,
                    finishedAt = finishedAt,
                    updatedAt = updatedAt,
                )
            if (rows == 0) return@transaction BookUpdateResult.NotFound
            val updatedBook = bookRepo.fetchById(bookId)

            BookUpdateResult.Success(BookResponse.from(updatedBook!!))
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
}

sealed class BookUpdateResult {
    data class Success(val book: BookResponse) : BookUpdateResult()

    object NotFound : BookUpdateResult()

    object Forbidden : BookUpdateResult()

    object Conflict : BookUpdateResult()

    data class ValidationError(val message: String) : BookUpdateResult()
}

sealed class BookDeleteResult {
    object Success : BookDeleteResult()

    object NotFound : BookDeleteResult()

    object Forbidden : BookDeleteResult()

    object DatabaseError : BookDeleteResult()
}
