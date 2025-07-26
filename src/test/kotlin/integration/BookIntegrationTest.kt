package integration

import dev.benkelenski.booked.createApp
import dev.benkelenski.booked.domain.responses.BookResponse
import dev.benkelenski.booked.loadConfig
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import dev.benkelenski.booked.repos.UserRepo
import dev.benkelenski.booked.routes.bookResponseLens
import dev.benkelenski.booked.routes.booksResponseLens
import io.kotest.matchers.be
import io.kotest.matchers.collections.shouldHaveSize
import org.http4k.base64Encode
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.kotest.shouldHaveBody
import org.http4k.kotest.shouldHaveStatus
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.reverseProxy
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import utils.FakeTokenProvider
import utils.TestDbUtils
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
        TestDbUtils.buildTables()
    }

    @AfterEach
    fun teardown() {
        TestDbUtils.dropTables()
    }

    @AfterAll
    fun teardownDb() {
        postgres.stop()
    }

    @Test
    fun `get all books`() {
        val user =
            UserRepo()
                .getOrCreateUser(
                    provider = "email",
                    providerUserId = "test@test.com",
                    email = "test@test.com",
                    name = "testuser",
                    password = "securepass",
                )

        val shelf = ShelfRepo().addShelf(userId = user.id, name = "test", description = null)

        val book1 =
            BookRepo()
                .saveBook(
                    userId = user.id,
                    googleId = "google1",
                    title = "test book 1",
                    authors = listOf("test author 1"),
                    shelfId = shelf!!.id,
                    thumbnailUrl = "null",
                )

        val book2 =
            BookRepo()
                .saveBook(
                    userId = user.id,
                    googleId = "google2",
                    title = "test book 2",
                    authors = listOf("test author 2"),
                    shelfId = shelf.id,
                    thumbnailUrl = "null",
                )

        val response =
            app(
                Request(Method.GET, "/api/v1/books")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(user.id)))
            )

        response shouldHaveStatus Status.OK
        response.shouldHaveBody(
            booksResponseLens,
            be(listOf(BookResponse.from(book1!!), BookResponse.from(book2!!))),
        )
    }

    @Test
    fun `get book - not found`() {
        Request(Method.GET, "/v1/books/9999").let(app).shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `get book - found`() {
        val user =
            UserRepo()
                .getOrCreateUser(
                    provider = "email",
                    providerUserId = "test@test.com",
                    email = "test@test.com",
                    name = "testuser",
                    password = "securepass",
                )

        val shelf = ShelfRepo().addShelf(userId = user.id, name = "test", description = null)

        val book1 =
            BookRepo()
                .saveBook(
                    userId = user.id,
                    googleId = "google1",
                    title = "test book 1",
                    authors = listOf("test author 1"),
                    shelfId = shelf!!.id,
                    thumbnailUrl = null,
                )

        val expectedBookRes = BookResponse.from(book1!!)

        val response =
            app(
                Request(Method.GET, "/api/v1/books/${book1.id}")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(user.id)))
            )

        response shouldHaveStatus Status.OK
        response.shouldHaveBody(bookResponseLens, be(expectedBookRes))
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
        val user =
            UserRepo()
                .getOrCreateUser(
                    provider = "email",
                    providerUserId = "test@test.com",
                    email = "test@test.com",
                    name = "testuser",
                    password = "securepass",
                )

        val shelf = ShelfRepo().addShelf(userId = user.id, name = "test", description = null)

        val book =
            BookRepo()
                .saveBook(
                    userId = user.id,
                    shelfId = shelf!!.id,
                    googleId = "google1",
                    title = "test book 1",
                    authors = listOf("test author 1"),
                    thumbnailUrl = null,
                )

        Request(Method.DELETE, "/api/v1/books/${book?.id}")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(2)))
            .let(app)
            .shouldHaveStatus(Status.FORBIDDEN)
    }

    @Test
    fun `delete book - success`() {
        val user =
            UserRepo()
                .getOrCreateUser(
                    provider = "email",
                    providerUserId = "test@test.com",
                    email = "test@test.com",
                    name = "testuser",
                    password = "securepass",
                )

        val shelf = ShelfRepo().addShelf(user.id, name = "test", description = null)

        val book =
            BookRepo()
                .saveBook(
                    userId = user.id,
                    googleId = "google1",
                    title = "test book 1",
                    authors = listOf("test author 1"),
                    shelfId = shelf!!.id,
                    thumbnailUrl = null,
                )

        BookRepo()
            .saveBook(
                userId = user.id,
                googleId = "google2",
                title = "test book 2",
                authors = listOf("test author 2"),
                shelfId = shelf.id,
                thumbnailUrl = null,
            )

        val response =
            app(
                Request(Method.DELETE, "/api/v1/books/${book?.id}")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(user.id)))
            )

        response shouldHaveStatus Status.NO_CONTENT
        BookRepo().getAllBooksByUser(userId = user.id) shouldHaveSize 1
    }
}
