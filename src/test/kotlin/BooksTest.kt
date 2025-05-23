//import dev.benkelenski.booked.*
//import io.kotest.matchers.be
//import io.mockk.every
//import io.mockk.mockk
//import org.http4k.core.Method
//import org.http4k.core.Request
//import org.http4k.core.Status
//import org.http4k.core.with
//import org.http4k.kotest.shouldHaveBody
//import org.http4k.kotest.shouldHaveStatus
//import org.junit.jupiter.api.Test
//
//private const val ULTIMATE_SEED_NUMBER = 42
//
//class BooksTest {
//    val mockBooksRepo = mockk<BooksRepo>()
//
//    val service = BooksService(
//        mockBooksRepo
//    )
//
//    val api = service.toApi()
//
//    @Test
//    fun `get all books`() {
//        val book1 = service.createBook(
//            BookRequest(
//                title = "Red Rising",
//                author = "Pierce Brown",
//            )
//        )
//
//        val book2 = service.createBook(
//            BookRequest(
//                title = "Mistborn",
//                author = "Brandon Sanderson",
//            )
//        )
//
//        every { mockBooksRepo.saveBook(any(), any()) } returns book1
//        every { mockBooksRepo.getAllBooks() } returns listOf(book1!!, book2!!)
//
//        val response = Request(Method.GET, "/v1/books")
//            .let(api)
//
//        response shouldHaveStatus Status.OK
//
//        response.shouldHaveBody(booksLens, be(listOf(book1, book2)))
//    }
//
//    @Test
//    fun `get book - not found`() {
//        every { mockBooksRepo.getBookById(any()) } returns null
//
//        Request(Method.GET, "/v1/books/9999")
//            .let(api)
//            .shouldHaveStatus(Status.NOT_FOUND)
//    }
//
//    @Test
//    fun `get book - found`() {
//        val book1 = service.createBook(
//            BookRequest(
//                title = "Red Rising",
//                author = "Pierce Brown",
//            )
//        )
//
//        service.createBook(
//            BookRequest(
//                title = "Mistborn",
//                author = "Brandon Sanderson",
//            )
//        )
//
//        every { mockBooksRepo.getBookById(any()) } returns book1
//
//        val response = Request(Method.GET, "/v1/books/${book1!!.id}")
//            .let(api)
//
//        response shouldHaveStatus Status.OK
//        response.shouldHaveBody(bookLens, be(book1))
//    }
//
//    @Test
//    fun `create book`() {
////        every { mockBooksRepo.saveBook(any(), any()) } returns
//
//        val response = Request(Method.POST, "/v1/books").with(
//            bookRequestLens of BookRequest(
//                title = "Red Rising",
//                author = "Pierce Brown",
//            )
//        ).let(api)
//
//        response shouldHaveStatus Status.CREATED
//    }
//
//    @Test
//    fun `delete book - not found`() {
//        Request(Method.DELETE, "/v1/books/82d36fa9-7287-4877-b74d-9383315de1b2")
//            .let(api)
//            .shouldHaveStatus(Status.NOT_FOUND)
//    }
//
//    @Test
//    fun `delete book - success`() {
//        service.createBook(
//            BookRequest(
//                title = "Red Rising",
//                author = "Pierce Brown"
//            )
//        )
//
//        val book2 = service.createBook(
//            BookRequest(
//                title = "Mistborn",
//                author = "Brandon Sanderson"
//            )
//        )
//
//        val response = Request(Method.DELETE, "/v1/books/2")
//            .let(api)
//
//        response shouldHaveStatus Status.OK
//    }
//}