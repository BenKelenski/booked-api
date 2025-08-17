package integration

import dev.benkelenski.booked.createApp
import dev.benkelenski.booked.domain.requests.BookRequest
import dev.benkelenski.booked.domain.requests.ShelfRequest
import dev.benkelenski.booked.domain.responses.BookResponse
import dev.benkelenski.booked.loadConfig
import dev.benkelenski.booked.models.Books
import dev.benkelenski.booked.models.Shelves
import dev.benkelenski.booked.routes.*
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.http4k.base64Encode
import org.http4k.core.*
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.kotest.shouldHaveStatus
import org.http4k.lens.bearerAuth
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.reverseProxy
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import testUtils.FakeTokenProvider
import testUtils.TestDbUtils
import testUtils.fakeGoogleBooks
import java.security.KeyPairGenerator

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShelfIntegrationTest {

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
        TestDbUtils.seedData()
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
    fun `get all shelves - unauthorized - no token`() {
        Request(Method.GET, "/api/v1/shelves").let(app).shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `get all shelves - unauthorized - bad token`() {
        Request(Method.GET, "/api/v1/shelves")
            .cookie(Cookie("access_token", "foo"))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `get all shelves - invalid shelf id`() {
        Request(Method.GET, "/api/v1/shelves/INVALID_SHELF_ID")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `get all shelves - success`() {
        val response =
            app(
                Request(Method.GET, "/api/v1/shelves")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            )

        response shouldHaveStatus Status.OK
        val responseBody = shelvesResLens(response)
        responseBody shouldHaveSize 3
    }

    @Test
    fun `get all shelves - none found`() {

        val response =
            app(
                Request(Method.GET, "/api/v1/shelves")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(2)))
            )

        response shouldHaveStatus Status.OK
        val responseBody = shelvesResLens(response)
        responseBody shouldHaveSize 0
    }

    @Test
    fun `get shelf - unauthorized - no token`() {
        Request(Method.GET, "/api/v1/shelves/9999").let(app).shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `get shelf - unauthorized - bad token`() {
        Request(Method.GET, "/api/v1/shelves/9999")
            .cookie(Cookie("access_token", "foo"))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `get shelf - invalid shelf id`() {
        Request(Method.GET, "/api/v1/shelves/INVALID_SHELF_ID")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `get shelf - not found`() {
        Request(Method.GET, "/v1/shelves/9999")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `get shelf - success`() {
        val response =
            app(
                Request(Method.GET, "/api/v1/shelves/1")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            )

        response shouldHaveStatus Status.OK
        val responseBody = shelfResLens(response)
        responseBody.name shouldBe "To Read"
    }

    @Test
    fun `create shelf - unauthorized - no token`() {
        Request(Method.POST, "/api/v1/shelves")
            .with(shelfRequestLens of ShelfRequest("shelf 1", null))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `create shelf - unauthorized - bad token`() {
        Request(Method.POST, "/api/v1/shelves")
            .cookie(Cookie("access_token", "foo"))
            .with(shelfRequestLens of ShelfRequest("shelf 1", null))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `create shelf - invalid shelf request`() {
        Request(Method.POST, "/api/v1/shelves")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `create shelf - invalid shelf request - empty name`() {
        Request(Method.POST, "/api/v1/shelves")
            .with(shelfRequestLens of ShelfRequest("", null))
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `create shelf - success`() {
        val response =
            app(
                Request(Method.POST, "/api/v1/shelves")
                    .with(shelfRequestLens of ShelfRequest("shelf 1", null))
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            )

        val responseBody = shelfResLens(response)

        response shouldHaveStatus Status.CREATED
        responseBody.name shouldBe "shelf 1"
        responseBody.description shouldBe null
        responseBody.createdAt shouldNotBe null
    }

    @Test
    fun `get books by shelf - unauthorized - no token`() {
        Request(Method.GET, "/api/v1/shelves/9999/books")
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `get books by shelf - unauthorized - bad token`() {
        Request(Method.GET, "/api/v1/shelves/9999/books")
            .cookie(Cookie("access_token", "foo"))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `get books by shelf - none found`() {
        val response =
            app(
                Request(Method.GET, "/api/v1/shelves/9999/books")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            )
        response.status shouldBe Status.OK
        val responseBody = booksResponseLens(response)
        responseBody shouldHaveSize 0
        responseBody shouldBe emptyList<BookResponse>()
    }

    @Test
    fun `get books by shelf - success`() {
        val response =
            app(
                Request(Method.GET, "/api/v1/shelves/1/books")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            )

        response.status shouldBe Status.OK

        val responseBody = booksResponseLens(response)

        responseBody shouldHaveSize 3
        responseBody[0].title shouldBe "Red Rising"
        responseBody[0].authors shouldBe listOf("Pierce Brown")
        responseBody[0].googleId shouldBe "google1"
        responseBody[1].title shouldBe "Golden Son"
        responseBody[1].authors shouldBe listOf("Pierce Brown")
        responseBody[1].googleId shouldBe "google2"
        responseBody[2].title shouldBe "Morning Star"
        responseBody[2].authors shouldBe listOf("Pierce Brown")
        responseBody[2].googleId shouldBe "google3"
    }

    @Test
    fun `add book to shelf - unauthorized - no token`() {
        Request(Method.POST, "/api/v1/shelves/9999/books")
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `add book to shelf - unauthorized - bad token`() {
        Request(Method.POST, "/api/v1/shelves/9999/books")
            .cookie(Cookie("access_token", "foo"))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `add book to shelf - invalid shelf id`() {
        Request(Method.POST, "/api/v1/shelves/INVALID_SHELF_ID/books")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `add book to shelf - bad request - no book request`() {
        Request(Method.POST, "/api/v1/shelves/9999/books")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `add book to shelf - bad request - missing google book id`() {
        Request(Method.POST, "/api/v1/shelves/9999/books")
            .with(bookRequestLens of BookRequest(" "))
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `add book to shelf - conflict - duplicate book`() {
        val googleBookId = "google1"

        Request(Method.POST, "/api/v1/shelves/1/books")
            .with(bookRequestLens of BookRequest(googleBookId))
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.CONFLICT)

        transaction {
            Books.selectAll()
                .where { (Books.shelfId eq 1) and (Books.googleId eq googleBookId) }
                .count() shouldBe 1
        }
    }

    @Test
    fun `add book to shelf - success`() {
        val googleBookId = "google4"

        val response =
            app(
                Request(Method.POST, "/api/v1/shelves/2/books")
                    .with(bookRequestLens of BookRequest(googleBookId))
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            )

        val book = bookResponseLens(response)
        book.id shouldBe 4
        book.googleId shouldBe googleBookId
        book.createdAt shouldNotBe null
        book.title shouldBe "book-$googleBookId"
        book.authors shouldBe listOf("author-$googleBookId")
        book.googleId shouldBe googleBookId
    }

    @Test
    fun `delete shelf - unauthorized - no token`() {
        Request(Method.DELETE, "/api/v1/shelves/999").let(app).shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete shelf - unauthorized - bad token`() {
        Request(Method.DELETE, "/api/v1/shelves/999")
            .bearerAuth("foo")
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete shelf - not found`() {
        Request(Method.DELETE, "/api/v1/shelves/999")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `delete shelf - forbidden`() {
        Request(Method.DELETE, "/api/v1/shelves/1")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(2)))
            .let(app)
            .shouldHaveStatus(Status.FORBIDDEN)
    }

    @Test
    fun `delete shelf - invalid shelf id`() {
        Request(Method.DELETE, "/api/v1/shelves/INVALID_SHELF_ID")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `delete shelf - success`() {
        Request(Method.DELETE, "/api/v1/shelves/1")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.NO_CONTENT)

        transaction { Shelves.selectAll().where { Shelves.userId eq 1 }.count() shouldBe 2 }
    }
}
