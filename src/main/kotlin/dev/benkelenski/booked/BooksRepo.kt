package dev.benkelenski.booked

class BookRepo(private val queries: BookQueries) {

    fun getAllBooks(): List<Book> = queries.findAllBooks()
        .executeAsList()
//        .map { it.toModel() }


    fun getBookById(id: Int): Book? = queries.findBookById(id)
        .executeAsOneOrNull()
//        ?.toModel()

    fun saveBook(title: String, author: String): Long = queries.insertBook(title, author).value

    fun deleteBook(id: Int): Long = queries.deleteBookById(id).value
}

private fun Books.toModel() = Book(
    id = id,
    title = title,
    author = author,
    createdAt = created_at.toInstant(),
)