package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.Book
import dev.benkelenski.booked.domain.ShelfType
import dev.benkelenski.booked.domain.requests.CompleteBookRequest
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

/** alias for [BookService.moveBook] */
typealias MoveBook = (userId: Int, bookId: Int, targetShelfId: Int) -> BookUpdateResult

/** alias for [BookService.updateBookProgress] */
typealias UpdateBookProgress = (userId: Int, bookId: Int, latestPage: Int) -> BookUpdateResult

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

    fun moveBook(userId: Int, bookId: Int, targetShelfId: Int): BookUpdateResult = transaction {
        val book =
            bookRepo.findByIdAndUser(bookId = bookId, userId = userId)
                ?: return@transaction validateBookExistence(bookId)

        val targetShelf =
            shelfRepo.fetchShelfById(userId, book.shelfId)
                ?: return@transaction BookUpdateResult.ShelfNotFound

        validateShelfMove(
                book = book,
                targetShelfId = targetShelfId,
            )
            ?.let {
                return@transaction it
            }

        val updatedBook = book.moveToShelf(targetShelfId, targetShelf.shelfType)

        bookRepo.updateBook(updatedBook)?.let { BookUpdateResult.Success(BookResponse.from(it)) }
            ?: BookUpdateResult.DatabaseError
    }

    fun updateBookProgress(userId: Int, bookId: Int, latestPage: Int): BookUpdateResult =
        transaction {
            val book =
                bookRepo.findByIdAndUser(bookId = bookId, userId = userId)
                    ?: return@transaction validateBookExistence(bookId)

            val currentShelf =
                shelfRepo.fetchShelfById(userId, book.shelfId)
                    ?: return@transaction BookUpdateResult.ShelfNotFound

            if (currentShelf.shelfType != ShelfType.READING)
                return@transaction BookUpdateResult.Forbidden

            val updatedBook = book.updateProgress(latestPage = latestPage)

            bookRepo.updateBook(updatedBook)?.let {
                BookUpdateResult.Success(BookResponse.from(it))
            } ?: BookUpdateResult.DatabaseError
        }

    fun completeBook(
        userId: Int,
        bookId: Int,
        completeBookRequest: CompleteBookRequest,
    ): BookUpdateResult = transaction {
        val book =
            bookRepo.findByIdAndUser(bookId = bookId, userId = userId)
                ?: return@transaction validateBookExistence(bookId)

        // Find a user's "FINISHED" shelf
        val finishedShelf =
            shelfRepo.findShelfByStatus(userId = userId, status = ShelfType.FINISHED)
                ?: return@transaction BookUpdateResult.ShelfNotFound

        validateShelfMove(
                book = book,
                targetShelfId = finishedShelf.id,
            )
            ?.let {
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
        book: Book,
        targetShelfId: Int,
    ): BookUpdateResult? {
        if (targetShelfId == book.shelfId) return null

        val hasDuplicate =
            bookRepo.existsDuplicateOnShelf(
                targetShelfId = targetShelfId,
                googleId = book.googleId,
                excludingBookId = book.id,
            )

        return if (hasDuplicate) BookUpdateResult.Conflict else null
    }
}

sealed class BookUpdateResult {
    data class Success(val book: BookResponse) : BookUpdateResult()

    object NotFound : BookUpdateResult()

    object ShelfNotFound : BookUpdateResult()

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
