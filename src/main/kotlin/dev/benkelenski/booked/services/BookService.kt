package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.Book
import dev.benkelenski.booked.domain.ShelfType
import dev.benkelenski.booked.domain.requests.CompleteBookRequest
import dev.benkelenski.booked.domain.responses.BookResponse
import dev.benkelenski.booked.domain.responses.toResponse
import dev.benkelenski.booked.external.google.GoogleBooksClient
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction

/** alias for [BookService.findBooksByShelf] */
typealias FindBooksByShelf = (userId: Int, shelves: List<Int>) -> List<BookResponse>

/** alias for [BookService.findBookById] */
typealias FindBookById = (bookId: Int) -> BookResponse?

/** alias for [BookService.addBook] */
typealias AddBook = (userId: Int, shelfId: Int, googleVolumeId: String) -> BookAddResult

/** alias for [BookService.deleteBook] */
typealias DeleteBook = (userId: Int, bookId: Int) -> BookDeleteResult

/** alias for [BookService.moveBook] */
typealias MoveBook = (userId: Int, bookId: Int, targetShelfId: Int) -> BookUpdateResult

/** alias for [BookService.updateBookProgress] */
typealias UpdateBookProgress = (userId: Int, bookId: Int, latestPage: Int) -> BookUpdateResult

/** alias for [BookService.completeBook] */
typealias CompleteBook =
    (userId: Int, bookId: Int, completeBookRequest: CompleteBookRequest) -> BookUpdateResult

class BookService(
    private val bookRepo: BookRepo,
    private val shelfRepo: ShelfRepo,
    private val googleBooksClient: GoogleBooksClient,
) {

    companion object {
        private val logger = KotlinLogging.logger {}

        private fun String.secureUrl(): String = replace("http://", "https://")
    }

    fun findBooksByShelf(userId: Int, shelves: List<Int>): List<BookResponse> = transaction {
        bookRepo.fetchAllBooksForUser(userId, shelves).map { book -> book.toResponse() }
    }

    fun findBookById(bookId: Int): BookResponse? = transaction {
        bookRepo.fetchById(bookId)?.toResponse()
    }

    fun addBook(userId: Int, shelfId: Int, googleVolumeId: String) = transaction {
        if (googleVolumeId.isBlank()) {
            return@transaction BookAddResult.ValidationError(
                listOf("Google Volume ID cannot be empty")
            )
        }

        // Check user doesn't already have book
        if (bookRepo.existsByGoogleIdAndUser(googleVolumeId, userId)) {
            return@transaction BookAddResult.Conflict
        }

        // Check user owns shelf
        shelfRepo.fetchShelvesWithBookCounts(userId, shelfId).firstOrNull()
            ?: return@transaction BookAddResult.ShelfNotFound

        // Get book details from Google Books API
        val volumeDto =
            googleBooksClient.getVolume(googleVolumeId)
                ?: return@transaction BookAddResult.BookNotFound

        val book =
            bookRepo.saveBook(
                userId = userId,
                shelfId = shelfId,
                googleId = volumeDto.id,
                title = volumeDto.volumeInfo.title,
                authors = volumeDto.volumeInfo.authors,
                thumbnailUrl = volumeDto.volumeInfo.imageLinks?.thumbnail?.secureUrl(),
                pageCount = volumeDto.volumeInfo.pageCount,
            ) ?: return@transaction BookAddResult.DatabaseError

        BookAddResult.Success(book.toResponse())
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
            shelfRepo
                .fetchShelvesWithBookCounts(userId = userId, shelfId = targetShelfId)
                .firstOrNull() ?: return@transaction BookUpdateResult.ShelfNotFound

        validateShelfMove(
                book = book,
                targetShelfId = targetShelfId,
            )
            ?.let {
                return@transaction it
            }

        val updatedBook =
            book.moveToShelf(targetShelfId = targetShelfId, targetShelfType = targetShelf.shelfType)

        bookRepo.updateBook(updatedBook)?.let { book ->
            BookUpdateResult.Success(book.toResponse())
        } ?: BookUpdateResult.DatabaseError
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
                shelfRepo
                    .fetchShelvesWithBookCounts(userId = userId, shelfId = book.shelfId)
                    .firstOrNull() ?: return@transaction BookUpdateResult.ShelfNotFound

            if (currentShelf.shelfType != ShelfType.READING)
                return@transaction BookUpdateResult.Forbidden

            try {
                val updatedBook = book.updateProgress(latestPage = latestPage)
                bookRepo.updateBook(updatedBook)?.let { book ->
                    BookUpdateResult.Success(book.toResponse())
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

        bookRepo.updateBook(updatedBook)?.let { book ->
            BookUpdateResult.Success(book.toResponse())
        } ?: BookUpdateResult.DatabaseError
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

sealed class BookAddResult {
    data class Success(val book: BookResponse) : BookAddResult()

    data class ValidationError(val errors: List<String>) : BookAddResult()

    object BookNotFound : BookAddResult()

    object ShelfNotFound : BookAddResult()

    object Conflict : BookAddResult()

    object DatabaseError : BookAddResult()
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
