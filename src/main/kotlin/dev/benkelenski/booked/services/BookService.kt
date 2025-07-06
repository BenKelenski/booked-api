package dev.benkelenski.booked.services

import dev.benkelenski.booked.clients.GoogleBooksClient
import dev.benkelenski.booked.domain.Book
import dev.benkelenski.booked.domain.BookRequest
import dev.benkelenski.booked.domain.DataBook
import dev.benkelenski.booked.repos.BookRepo

/** alias for [BookService.getBookById] */
typealias GetBookById = (bookId: Int) -> Book?

/** alias for [BookService.getAllBooks] */
typealias GetAllBooks = () -> List<Book>

/** alias for [BookService.createBook] */
typealias CreateBook = (userId: String, bookRequest: BookRequest) -> Book?

/** alias for [BookService.deleteBook] */
typealias DeleteBook = (userId: String, bookId: Int) -> BookDeleteResult

/** alias for [BookService.searchBooks] */
typealias SearchBooks = (query: String?) -> Array<DataBook>?

class BookService(
    private val bookRepo: BookRepo,
    private val googleBooksClient: GoogleBooksClient,
) {

    fun getBookById(bookId: Int): Book? = bookRepo.getBookById(bookId)

    fun getAllBooks(): List<Book> = bookRepo.getAllBooks()

    fun createBook(userId: String, bookRequest: BookRequest): Book? =
        bookRepo.saveBook(
            userId = userId,
            title = bookRequest.title,
            author = bookRequest.author,
            shelfId = bookRequest.shelfId,
        )

    fun deleteBook(userId: String, bookId: Int): BookDeleteResult {
        val book = bookRepo.getBookById(bookId) ?: return BookDeleteResult.NotFound
        if (book.userId != userId) return BookDeleteResult.Forbidden
        return if (bookRepo.deleteBook(bookId) == 1) {
            BookDeleteResult.Success
        } else {
            BookDeleteResult.Failure("Failed to delete $book")
        }
    }

    fun searchBooks(query: String?): Array<DataBook>? = googleBooksClient.search(query)
}

sealed class BookDeleteResult {
    object Success : BookDeleteResult()

    object NotFound : BookDeleteResult()

    object Forbidden : BookDeleteResult()

    data class Failure(val reason: String) : BookDeleteResult()
}
