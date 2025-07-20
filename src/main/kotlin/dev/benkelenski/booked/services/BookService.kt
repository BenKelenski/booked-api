package dev.benkelenski.booked.services

import dev.benkelenski.booked.clients.GoogleBooksClient
import dev.benkelenski.booked.domain.Book
import dev.benkelenski.booked.domain.BookRequest
import dev.benkelenski.booked.domain.DataBook
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import io.github.oshai.kotlinlogging.KotlinLogging

/** alias for [BookService.getBookById] */
typealias GetBookById = (bookId: Int) -> Book?

/** alias for [BookService.getAllBooks] */
typealias GetAllBooks = () -> List<Book>

/** alias for [BookService.createBook] */
typealias CreateBook = (userId: Int, bookRequest: BookRequest) -> BookCreateResult

/** alias for [BookService.deleteBook] */
typealias DeleteBook = (userId: Int, bookId: Int) -> BookDeleteResult

/** alias for [BookService.searchBooks] */
typealias SearchBooks = (query: String?) -> Array<DataBook>?

class BookService(
    private val bookRepo: BookRepo,
    private val shelfRepo: ShelfRepo,
    private val googleBooksClient: GoogleBooksClient,
) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    fun getAllBooks(): List<Book> = bookRepo.getAllBooks()

    fun getBookById(bookId: Int): Book? = bookRepo.getBookById(bookId)

    fun createBook(userId: Int, bookRequest: BookRequest): BookCreateResult {
        shelfRepo.getShelfById(userId, bookRequest.shelfId) ?: return BookCreateResult.ShelfNotFound

        val newBook =
            bookRepo.saveBook(
                title = bookRequest.title,
                author = bookRequest.author,
                shelfId = bookRequest.shelfId,
            ) ?: return BookCreateResult.DatabaseError

        return BookCreateResult.Success(newBook)
    }

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

    fun searchBooks(query: String?): Array<DataBook>? = googleBooksClient.search(query)
}

sealed class BookCreateResult {
    data class Success(val book: Book) : BookCreateResult()

    object ShelfNotFound : BookCreateResult()

    object DatabaseError : BookCreateResult()
}

sealed class BookDeleteResult {
    object Success : BookDeleteResult()

    object NotFound : BookDeleteResult()

    object Forbidden : BookDeleteResult()

    object DatabaseError : BookDeleteResult()
}
