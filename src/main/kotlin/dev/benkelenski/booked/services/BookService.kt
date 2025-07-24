package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.responses.BookResponse
import dev.benkelenski.booked.external.google.GoogleBooksClient
import dev.benkelenski.booked.external.google.dto.VolumeDto
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.UserRepo
import io.github.oshai.kotlinlogging.KotlinLogging

/** alias for [BookService.getBookById] */
typealias GetBookById = (userId: Int, bookId: Int) -> BookResponse?

/** alias for [BookService.getAllBooksForUser] */
typealias GetAllBooksForUser = (userId: Int) -> List<BookResponse>

/** alias for [BookService.deleteBook] */
typealias DeleteBook = (userId: Int, bookId: Int) -> BookDeleteResult

/** alias for [BookService.searchBooks] */
typealias SearchBooks = (userId: Int, query: String?) -> Array<VolumeDto>?

class BookService(
    private val bookRepo: BookRepo,
    private val userRepo: UserRepo,
    private val googleBooksClient: GoogleBooksClient,
) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    // TODO: do user exist checks for both get functions
    fun getAllBooksForUser(userId: Int): List<BookResponse> =
        bookRepo.getAllBooksByUser(userId).map { BookResponse.from(it) }

    fun getBookById(bookId: Int): BookResponse? =
        bookRepo.getBookById(bookId)?.let { BookResponse.from(it) }

    fun deleteBook(userId: Int, bookId: Int): BookDeleteResult =
        try {
            val deletedCount = bookRepo.deleteByIdAndUser(bookId, userId)

            when {
                deletedCount == 1 -> BookDeleteResult.Success
                !bookRepo.existsById(bookId) -> BookDeleteResult.NotFound
                else -> BookDeleteResult.Forbidden
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete book: $bookId" }
            BookDeleteResult.DatabaseError
        }

    fun searchBooks(userId: Int, query: String?): Array<VolumeDto>? {
        if (!userRepo.existsById(userId)) {
            logger.warn { "User $userId not found" }
            return null
        }

        return googleBooksClient.search(query)
    }
}

sealed class BookDeleteResult {
    object Success : BookDeleteResult()

    object NotFound : BookDeleteResult()

    object Forbidden : BookDeleteResult()

    object DatabaseError : BookDeleteResult()
}
