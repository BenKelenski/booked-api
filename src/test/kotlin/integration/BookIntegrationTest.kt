package integration

import dev.benkelenski.booked.createApp
import dev.benkelenski.booked.models.BookRequest
import dev.benkelenski.booked.models.BookTable
import dev.benkelenski.booked.models.ShelfTable
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.routes.bookLens
import dev.benkelenski.booked.routes.bookRequestLens
import dev.benkelenski.booked.routes.booksLens
import io.kotest.matchers.be
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.kotest.shouldHaveBody
import org.http4k.kotest.shouldHaveStatus
import org.http4k.routing.RoutingHttpHandler
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookIntegrationTest {

    private lateinit var postgres: PostgreSQLContainer<*>

    private lateinit var app: RoutingHttpHandler

    @BeforeAll
    fun setupDb() {
        postgres =
            PostgreSQLContainer("postgres:17.5-alpine3.21").apply {
                withDatabaseName("testdb")
                withUsername("test")
                withPassword("test")
                start()
            }

        Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password,
        )

        app = createApp()
    }

    @BeforeEach
    fun setup() {
        transaction { SchemaUtils.create(BookTable, ShelfTable) }
    }

    @AfterEach
    fun teardown() {
        transaction { SchemaUtils.drop(BookTable, ShelfTable) }
    }

    @AfterAll
    fun teardownDb() {
        postgres.stop()
    }

    @Test
    fun `get all books`() {
        val book1 = BookRepo().saveBook("test book 1", "test author 1")
        val book2 = BookRepo().saveBook("test book 2", "test author 2")

        val response = app(Request(Method.GET, "/api/v1/books"))

        response shouldHaveStatus Status.OK
        response.shouldHaveBody(booksLens, be(listOf(book1, book2)))
    }

    @Test
    fun `get book - not found`() {
        val response = app(Request(Method.GET, "/v1/books/9999"))

        response shouldHaveStatus Status.NOT_FOUND
    }

    @Test
    fun `get book - found`() {
        val book1 = BookRepo().saveBook("test book 1", "test author 1")

        val response = app(Request(Method.GET, "/api/v1/books/${book1?.id}"))

        response shouldHaveStatus Status.OK
        response.shouldHaveBody(bookLens, be(book1))
    }

    @Test
    fun `create book`() {
        val response =
            app(
                Request(Method.POST, "/api/v1/books")
                    .with(
                        bookRequestLens of
                            BookRequest(title = "Red Rising", author = "Pierce Brown ")
                    )
            )

        response shouldHaveStatus Status.CREATED
        response.bodyString() shouldContain "Red Rising"
        response.bodyString() shouldContain "Pierce Brown"
    }

    @Test
    fun `delete book - not found`() {
        val response = app(Request(Method.DELETE, "/api/v1/books/999"))

        response shouldHaveStatus Status.NOT_FOUND
    }

    @Test
    fun `delete book - success`() {
        BookRepo().saveBook("test book 1", "test author 1")
        val book2 = BookRepo().saveBook("test book 2", "test author 2")

        val response = app(Request(Method.DELETE, "/api/v1/books/${book2?.id}"))

        response shouldHaveStatus Status.OK
        BookRepo().getAllBooks() shouldHaveSize 1
    }
}
