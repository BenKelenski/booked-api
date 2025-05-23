package dev.benkelenski.booked

import java.time.Clock
import kotlin.random.Random

class BookService(private val bookRepo: BookRepo, private val clock: Clock, private val random: Random) {

    fun getBook(id: Int): Book? = bookRepo.getBookById(id)

    fun getBooks(): List<Book> = bookRepo.getAllBooks()

    fun deleteBook(id: Int): Boolean {
        val count = bookRepo.deleteBook(id)
        print("Deleted $count books")
        return count.toInt() == 1
    }

    fun createBook(bookRequest: BookRequest): Boolean {
        val count = bookRepo.saveBook(title = bookRequest.title, author = bookRequest.author)
        print("Created $count books")
        return count.toInt() == 1
    }
}
