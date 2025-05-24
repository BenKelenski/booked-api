package dev.benkelenski.booked

class BooksRepo(private val queries: BooksQueries) {

    fun getAllBooks(): List<Book> = queries.findAllBooks()
        .executeAsList()
        .map { it.toModel() }


    fun getBookById(id: Int): Book? = queries.findBookById(id)
        .executeAsOneOrNull()
        ?.toModel()

    fun saveBook(title: String, author: String): Book? =
        queries.insertBook(title, author)
            .executeAsOneOrNull()?.toModel()

    fun deleteBook(id: Int): Long = queries.deleteBookById(id).value
}

private fun Books.toModel() = Book(
    id = id,
    title = title,
    author = author,
    createdAt = created_at.toInstant(),
)