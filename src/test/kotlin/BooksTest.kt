import dev.benkelenski.booked.*
import io.kotest.matchers.be
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.kotest.shouldHaveBody
import org.http4k.kotest.shouldHaveStatus
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import kotlin.random.Random

private const val ULTIMATE_SEED_NUMBER = 42

class BooksTest {

    val service = BookService(
        Clock.fixed(Instant.parse("2025-03-25T12:00:00Z"), ZoneOffset.UTC),
        Random(ULTIMATE_SEED_NUMBER)
    )

    val api = service.toApi()

    @Test
    fun `get all books`() {
        val book1 = service.createBook(
            BookRequest(
                title = "Red Rising",
                author = "Pierce Brown",
                publisher = "Del Rey",
                isbn = "1234567890",
            )
        )

        val book2 = service.createBook(
            BookRequest(
                title = "Mistborn",
                author = "Brandon Sanderson",
                publisher = "Tor",
                isbn = "2345678901",
            )
        )

        val response = Request(Method.GET, "/v1/books")
            .let(api)

        response shouldHaveStatus Status.OK

        response.shouldHaveBody(booksLens, be(listOf(book1, book2)))
    }

    @Test
    fun `get book - not found`() {
        Request(Method.GET, "/v1/books/${UUID.randomUUID()}")
            .let(api)
            .shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `get book - found`() {
        val book1 = service.createBook(
            BookRequest(
                title = "Red Rising",
                author = "Pierce Brown",
                publisher = "Del Rey",
                isbn = "1234567890",
            )
        )

        service.createBook(
            BookRequest(
                title = "Mistborn",
                author = "Brandon Sanderson",
                publisher = "Tor",
                isbn = "2345678901",
            )
        )

        val response = Request(Method.GET, "/v1/books/${book1.id}")
            .let(api)

        response shouldHaveStatus Status.OK
        response.shouldHaveBody(bookLens, be(book1))
    }

    @Test
    fun `create book`() {
        val response = Request(Method.POST, "/v1/books").with(
            bookRequestLens of BookRequest(
                title = "Red Rising",
                author = "Pierce Brown",
                publisher = "Del Rey",
                isbn = "1234567890",
            )
        ).let(api)

        response shouldHaveStatus Status.CREATED
        response.bodyString() shouldContain "Red Rising"
        response.bodyString() shouldContain "Pierce Brown"
    }

    @Test
    fun `delete book - not found`() {
        Request(Method.DELETE, "/v1/books/82d36fa9-7287-4877-b74d-9383315de1b2")
            .let(api)
            .shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `delete book - success`() {
        service.createBook(
            BookRequest(
                title = "Red Rising",
                author = "Pierce Brown",
                publisher = "Del Rey",
                isbn = "1234567890",
            )
        )

        val book2 = service.createBook(
            BookRequest(
                title = "Mistborn",
                author = "Brandon Sanderson",
                publisher = "Tor",
                isbn = "2345678901",
            )
        )

        val response = Request(Method.DELETE, "/v1/books/${book2.id}")
            .let(api)

        response shouldHaveStatus Status.OK
        response.shouldHaveBody(bookLens, be(book2))
        service.getBooks().shouldHaveSize(1)
    }
}