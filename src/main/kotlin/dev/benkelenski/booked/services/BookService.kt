package dev.benkelenski.booked.services

import dev.benkelenski.booked.models.Book
import dev.benkelenski.booked.models.BookRequest
import dev.benkelenski.booked.repos.BooksRepo

/** alias for [BookService.getBook] */
typealias GetBook = (id: Int) -> Book?

/** alias for [BookService.getAllBooks] */
typealias GetAllBooks = () -> List<Book>

/** alias for [BookService.createBook] */
typealias CreateBook = (bookRequest: BookRequest) -> Book?

/** alias for [BookService.deleteBook] */
typealias DeleteBook = (id: Int) -> Boolean

class BookService(private val booksRepo: BooksRepo) {

  fun getBook(id: Int): Book? = booksRepo.getBookById(id)

  fun getAllBooks(): List<Book> = booksRepo.getAllBooks()

  fun createBook(bookRequest: BookRequest): Book? =
    booksRepo.saveBook(title = bookRequest.title, author = bookRequest.author)

  fun deleteBook(id: Int): Boolean = booksRepo.deleteBook(id) == 1
}
