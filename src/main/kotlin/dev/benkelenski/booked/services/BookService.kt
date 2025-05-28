package dev.benkelenski.booked.services

import dev.benkelenski.booked.models.Book
import dev.benkelenski.booked.models.BookRequest
import dev.benkelenski.booked.repos.BookRepo

/** alias for [BookService.getBook] */
typealias GetBook = (id: Int) -> Book?

/** alias for [BookService.getAllBooks] */
typealias GetAllBooks = () -> List<Book>

/** alias for [BookService.createBook] */
typealias CreateBook = (bookRequest: BookRequest) -> Book?

/** alias for [BookService.deleteBook] */
typealias DeleteBook = (id: Int) -> Boolean

class BookService(private val bookRepo: BookRepo) {

    fun getBook(id: Int): Book? = bookRepo.getBookById(id)

    fun getAllBooks(): List<Book> = bookRepo.getAllBooks()

    fun createBook(bookRequest: BookRequest): Book? =
        bookRepo.saveBook(
            title = bookRequest.title,
            author = bookRequest.author,
            shelfId = bookRequest.shelfId,
        )

    fun deleteBook(id: Int): Boolean = bookRepo.deleteBook(id) == 1
}
