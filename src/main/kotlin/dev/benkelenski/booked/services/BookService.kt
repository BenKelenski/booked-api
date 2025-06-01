package dev.benkelenski.booked.services

import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.exceptions.JWTVerificationException
import dev.benkelenski.booked.models.Book
import dev.benkelenski.booked.models.BookRequest
import dev.benkelenski.booked.repos.BookRepo

/** alias for [BookService.getBook] */
typealias GetBook = (id: Int) -> Book?

/** alias for [BookService.getAllBooks] */
typealias GetAllBooks = () -> List<Book>

/** alias for [BookService.createBook] */
typealias CreateBook = (userId: String, bookRequest: BookRequest) -> Book?

/** alias for [BookService.deleteBook] */
typealias DeleteBook = (userId: String, id: Int) -> DeleteResult

/** alias for [BookService.verify] */
typealias Verify = (token: String) -> String?

class BookService(private val bookRepo: BookRepo, private val jwtVerifier: JWTVerifier) {

    fun getBook(id: Int): Book? = bookRepo.getBookById(id)

    fun getAllBooks(): List<Book> = bookRepo.getAllBooks()

    fun createBook(userId: String, bookRequest: BookRequest): Book? =
        bookRepo.saveBook(
            userId = userId,
            title = bookRequest.title,
            author = bookRequest.author,
            shelfId = bookRequest.shelfId,
        )

    fun deleteBook(userId: String, id: Int): DeleteResult {
        val book = bookRepo.getBookById(id) ?: return DeleteResult.NotFound
        if (book.userId != userId) return DeleteResult.Forbidden
        return if (bookRepo.deleteBook(id) == 1) {
            DeleteResult.Success
        } else {
            DeleteResult.Failure("Failed to delete $book")
        }
    }

    fun verify(token: String): String? {
        return try {
            jwtVerifier.verify(token).subject
        } catch (e: JWTVerificationException) {
            null
        }
    }
}

sealed class DeleteResult {
    object Success : DeleteResult()

    object NotFound : DeleteResult()

    object Forbidden : DeleteResult()

    data class Failure(val reason: String) : DeleteResult()
}
