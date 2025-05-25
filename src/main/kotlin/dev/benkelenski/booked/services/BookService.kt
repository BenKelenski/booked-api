package dev.benkelenski.booked.services

import dev.benkelenski.booked.models.Book
import dev.benkelenski.booked.models.BookRequest
import dev.benkelenski.booked.repos.BooksRepo

class BookService(private val booksRepo: BooksRepo) {

    fun getBook(id: Int): Book? = booksRepo.getBookById(id)

    fun getBooks(): List<Book> = booksRepo.getAllBooks()

    fun deleteBook(id: Int): Boolean {
        val count = booksRepo.deleteBook(id)
        print("Deleted $count books")
        return count == 1
    }

    fun createBook(bookRequest: BookRequest): Book? =
        booksRepo.saveBook(title = bookRequest.title, author = bookRequest.author)
}