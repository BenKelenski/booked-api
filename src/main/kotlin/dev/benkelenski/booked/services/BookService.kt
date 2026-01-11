package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.Book
import dev.benkelenski.booked.domain.ShelfType
import dev.benkelenski.booked.domain.requests.CompleteBookRequest
import dev.benkelenski.booked.domain.responses.BookResponse
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction

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

    fun deleteBook(userId: Int, bookId: Int): BookDeleteResult = transaction {
        val deletedCount = bookRepo.deleteByIdAndUser(userId = userId, bookId = bookId)

        when {
            deletedCount == 1 -> BookDeleteResult.Success
            !bookRepo.existsById(bookId) -> BookDeleteResult.NotFound
            else -> BookDeleteResult.Forbidden
        }
    }

    fun moveBook(userId: Int, bookId: Int, targetShelfId: Int): BookUpdateResult = transaction {
        val book =
            bookRepo.findByIdAndUser(bookId = bookId, userId = userId)
                ?: return@transaction validateBookExistence(bookId)

        val targetShelf =
            shelfRepo.fetchShelfById(userId = userId, shelfId = targetShelfId)
                ?: return@transaction BookUpdateResult.ShelfNotFound

        validateShelfMove(
                book = book,
                targetShelfId = targetShelfId,
            )
            ?.let {
                return@transaction it
            }

        val updatedBook =
            book.moveToShelf(targetShelfId = targetShelfId, targetShelfType = targetShelf.shelfType)

        bookRepo.updateBook(updatedBook)?.let { BookUpdateResult.Success(BookResponse.from(it)) }
            ?: BookUpdateResult.DatabaseError
    }

    fun updateBookProgress(userId: Int, bookId: Int, latestPage: Int): BookUpdateResult =
        transaction {
            val book =
                bookRepo.findByIdAndUser(bookId = bookId, userId = userId)
                    ?: return@transaction validateBookExistence(bookId)

            if (latestPage < 0)
                return@transaction BookUpdateResult.ValidationError(
                    listOf("Page must be a positive integer")
                )

            val currentShelf =
                shelfRepo.fetchShelfById(userId = userId, shelfId = book.shelfId)
                    ?: return@transaction BookUpdateResult.ShelfNotFound

            if (currentShelf.shelfType != ShelfType.READING)
                return@transaction BookUpdateResult.Forbidden

            try {
                val updatedBook = book.updateProgress(latestPage = latestPage)
                bookRepo.updateBook(updatedBook)?.let {
                    BookUpdateResult.Success(BookResponse.from(it))
                } ?: BookUpdateResult.DatabaseError
            } catch (e: IllegalArgumentException) {
                BookUpdateResult.ValidationError(listOf(e.message ?: "Invalid progress update"))
            }
        }

    fun completeBook(
        userId: Int,
        bookId: Int,
        completeBookRequest: CompleteBookRequest,
    ): BookUpdateResult = transaction {
        val book =
            bookRepo.findByIdAndUser(bookId = bookId, userId = userId)
                ?: return@transaction validateBookExistence(bookId)

        val validationErrors = completeBookRequest.validate()
        if (validationErrors.isNotEmpty()) {
            logger.warn { "Book completion validation failed: $validationErrors" }
            return@transaction BookUpdateResult.ValidationError(validationErrors)
        }

        val finishedShelf =
            shelfRepo.findShelfByType(userId = userId, type = ShelfType.FINISHED)
                ?: return@transaction BookUpdateResult.ShelfNotFound

        validateShelfMove(
                book = book,
                targetShelfId = finishedShelf.id,
            )
            ?.let {
                return@transaction it
            }

        val updatedBook =
            book.complete(
                shelfId = finishedShelf.id,
                rating = completeBookRequest.rating,
                review = completeBookRequest.review,
            )

        bookRepo.updateBook(updatedBook)?.let { BookUpdateResult.Success(BookResponse.from(it)) }
            ?: BookUpdateResult.DatabaseError
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

    data class ValidationError(val errors: List<String>) : BookUpdateResult()

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
