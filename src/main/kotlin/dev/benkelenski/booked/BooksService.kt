package dev.benkelenski.booked

class BooksService(private val booksRepo: BooksRepo) {

    fun getBook(id: Int): Book? = booksRepo.getBookById(id)

    fun getBooks(): List<Book> = booksRepo.getAllBooks()

    fun deleteBook(id: Int): Boolean {
        val count = booksRepo.deleteBook(id)
        print("Deleted $count books")
        return count.toInt() == 1
    }

    fun createBook(bookRequest: BookRequest): Book? =
        booksRepo.saveBook(title = bookRequest.title, author = bookRequest.author)
}
