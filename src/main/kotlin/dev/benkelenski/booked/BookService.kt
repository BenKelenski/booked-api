package dev.benkelenski.booked

import java.time.Clock
import java.util.*
import kotlin.random.Random

private const val UUID_LENGTH = 40

class BookService(private val clock: Clock, private val random: Random) {

    private val books = mutableListOf<Book>()

    fun getBook(id: UUID): Book? = books.find { it.id == id }

    fun getBooks(): List<Book> = books.toList()

    fun deleteBook(id: UUID): Book? {
        val book = books.find { it.id == id }
        if (!books.remove(book)) return null
        return book
    }

    fun createBook(bookRequest: BookRequest): Book {
        val book = Book(
            id = UUID.nameUUIDFromBytes(random.nextBytes(UUID_LENGTH)),
            title = bookRequest.title,
            author = bookRequest.author,
            createdAt = clock.instant(),
        )
        books += book
        return book
    }
}
