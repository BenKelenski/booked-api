package integration

import dev.benkelenski.booked.createApp
import dev.benkelenski.booked.domain.BookRequest
import dev.benkelenski.booked.loadConfig
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import dev.benkelenski.booked.routes.bookLens
import dev.benkelenski.booked.routes.bookRequestLens
import dev.benkelenski.booked.routes.booksLens
import io.kotest.matchers.be
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.http4k.base64Encode
import org.http4k.core.*
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.kotest.shouldHaveBody
import org.http4k.kotest.shouldHaveStatus
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.reverseProxy
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import utils.FakeDbUtils
import utils.FakeTokenProvider
import utils.fakeGoogleBooks
import java.security.KeyPairGenerator

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookIntegrationTest {

    private lateinit var postgres: PostgreSQLContainer<*>

    private lateinit var app: RoutingHttpHandler

    private val keyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private val config = loadConfig("test")

    private val fakeTokenProvider = FakeTokenProvider()

    @BeforeAll
    fun setupApp() {
        postgres =
            PostgreSQLContainer("postgres:17.5-alpine3.21").apply {
                withDatabaseName(config.database.url)
                withUsername(config.database.user)
                withPassword(config.database.password)
                start()
            }

        Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password,
        )

        config.apply { server.auth.google.publicKey = keyPair.public.encoded.base64Encode() }
        app =
            createApp(
                config = config,
                internet =
                    reverseProxy(Uri.of(config.client.googleApisHost).host to fakeGoogleBooks()),
                fakeTokenProvider,
            )
    }

    @BeforeEach
    fun setup() {
        FakeDbUtils.buildTables()
    }

    @AfterEach
    fun teardown() {
        FakeDbUtils.dropTables()
    }

    @AfterAll
    fun teardownDb() {
        postgres.stop()
    }

    @Test
    fun `get all books`() {
        val shelf = ShelfRepo().addShelf(userId = 1, name = "test", description = null)

        val book1 =
            BookRepo()
                .saveBook(title = "test book 1", author = "test author 1", shelfId = shelf!!.id)

        val book2 =
            BookRepo().saveBook(title = "test book 2", author = "test author 2", shelfId = shelf.id)

        val response = app(Request(Method.GET, "/api/v1/books"))

        response shouldHaveStatus Status.OK
        response.shouldHaveBody(booksLens, be(listOf(book1, book2)))
    }

    @Test
    fun `get book - not found`() {
        Request(Method.GET, "/v1/books/9999").let(app).shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `get book - found`() {
        val shelf = ShelfRepo().addShelf(userId = 1, name = "test", description = null)

        val book1 =
            BookRepo()
                .saveBook(title = "test book 1", author = "test author 1", shelfId = shelf!!.id)

        val response = app(Request(Method.GET, "/api/v1/books/${book1?.id}"))

        response shouldHaveStatus Status.OK
        response.shouldHaveBody(bookLens, be(book1))
    }

    @Test
    fun `create book - unauthorized`() {

        Request(Method.POST, "/api/v1/books")
            .with(
                bookRequestLens of
                    BookRequest(title = "Red Rising", author = "Pierce Brown", shelfId = 1)
            )
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `create book`() {
        val userId = 1
        val shelf = ShelfRepo().addShelf(userId = userId, name = "test", description = null)

        val response =
            app(
                Request(Method.POST, "/api/v1/books")
                    .with(
                        bookRequestLens of
                            BookRequest(
                                title = "Red Rising",
                                author = "Pierce Brown",
                                shelfId = shelf!!.id,
                            )
                    )
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(userId)))
            )

        val responseBody = bookLens(response)

        response shouldHaveStatus Status.CREATED
        responseBody.id shouldBe 1
        responseBody.shelfId shouldBe shelf.id
        responseBody.title shouldBe "Red Rising"
        responseBody.author shouldBe "Pierce Brown"
        responseBody.createdAt shouldNotBe null
    }

    @Test
    fun `delete book - unauthorized due to no token`() {
        Request(Method.DELETE, "/api/v1/books/999").let(app).shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete book - unauthorized due to bad token`() {
        Request(Method.DELETE, "/api/v1/books/999")
            .cookie(Cookie("access_token", "foo"))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete book - not found`() {
        Request(Method.DELETE, "/api/v1/books/999")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `delete book - forbidden`() {
        val shelf = ShelfRepo().addShelf(userId = 1, name = "test", description = null)

        val book =
            BookRepo()
                .saveBook(title = "test book 1", author = "test author 1", shelfId = shelf!!.id)

        Request(Method.DELETE, "/api/v1/books/${book?.id}")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(2)))
            .let(app)
            .shouldHaveStatus(Status.FORBIDDEN)
    }

    @Test
    fun `delete book - success`() {
        val userId = 1

        val shelf = ShelfRepo().addShelf(userId, name = "test", description = null)

        BookRepo().saveBook(title = "test book 1", author = "test author 1", shelfId = shelf!!.id)

        val book2 = BookRepo().saveBook("test book 2", "test author 2", shelfId = shelf.id)

        val response =
            app(
                Request(Method.DELETE, "/api/v1/books/${book2?.id}")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(userId)))
            )

        response shouldHaveStatus Status.NO_CONTENT
        BookRepo().getAllBooks() shouldHaveSize 1
    }
}
