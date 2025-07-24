package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.responses.BookResponse
import dev.benkelenski.booked.external.google.GoogleBooksClient
import dev.benkelenski.booked.external.google.dto.VolumeDto
import dev.benkelenski.booked.repos.BookRepo
import io.github.oshai.kotlinlogging.KotlinLogging

/** alias for [BookService.getBookById] */
typealias GetBookById = (bookId: Int) -> BookResponse?

/** alias for [BookService.getAllBooks] */
typealias GetAllBooks = () -> List<BookResponse>

/** alias for [BookService.deleteBook] */
typealias DeleteBook = (userId: Int, bookId: Int) -> BookDeleteResult

/** alias for [BookService.searchBooks] */
typealias SearchBooks = (query: String?) -> Array<VolumeDto>?

class BookService(
    private val bookRepo: BookRepo,
    private val googleBooksClient: GoogleBooksClient,
) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    fun getAllBooks(): List<BookResponse> = bookRepo.getAllBooks().map { BookResponse.from(it) }

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

    fun searchBooks(query: String?): Array<VolumeDto>? = googleBooksClient.search(query)
}

sealed class BookDeleteResult {
    object Success : BookDeleteResult()

    object NotFound : BookDeleteResult()

    object Forbidden : BookDeleteResult()

    object DatabaseError : BookDeleteResult()
}
