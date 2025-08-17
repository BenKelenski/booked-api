package integration

import dev.benkelenski.booked.createApp
import dev.benkelenski.booked.domain.ReadingStatus
import dev.benkelenski.booked.domain.requests.UpdateBookPatch
import dev.benkelenski.booked.loadConfig
import dev.benkelenski.booked.models.Books
import dev.benkelenski.booked.routes.bookResponseLens
import dev.benkelenski.booked.routes.booksResponseLens
import dev.benkelenski.booked.routes.updateBookPatchLens
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.http4k.base64Encode
import org.http4k.core.*
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.kotest.shouldHaveStatus
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.reverseProxy
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import testUtils.FakeTokenProvider
import testUtils.TestDbUtils
import testUtils.fakeGoogleBooks
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
    fun `get all books - unauthorized - no token`() {
        Request(Method.GET, "/api/v1/books").let(app).shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `get all books - unauthorized - bad token`() {
        Request(Method.GET, "/api/v1/books")
            .cookie(Cookie("access_token", "foo"))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `get all books - success`() {
        val response =
            app(
                Request(Method.GET, "/api/v1/books")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            )

        response shouldHaveStatus Status.OK

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
    fun `get book - unauthorized - no token`() {
        Request(Method.GET, "/api/v1/books/9999").let(app).shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `get book - unauthorized - bad token`() {
        Request(Method.GET, "/api/v1/books/9999")
            .cookie(Cookie("access_token", "foo"))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `get book - invalid book id`() {
        Request(Method.GET, "/api/v1/books/INVALID_BOOK_ID")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `get book - not found`() {
        Request(Method.GET, "/v1/books/9999").let(app).shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `get book - found`() {
        val response =
            app(
                Request(Method.GET, "/api/v1/books/1")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            )

        response shouldHaveStatus Status.OK

        val responseBody = bookResponseLens(response)
        responseBody.title shouldBe "Red Rising"
        responseBody.authors shouldBe listOf("Pierce Brown")
        responseBody.googleId shouldBe "google1"
    }

    @Test
    fun `update book - unauthorized - no token`() {
        Request(Method.PATCH, "/api/v1/books/9999").let(app).shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `update book - unauthorized - bad token`() {
        Request(Method.PATCH, "/api/v1/books/9999")
            .cookie(Cookie("access_token", "foo"))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `update book - bad request - invalid book id`() {
        Request(Method.PATCH, "/api/v1/books/INVALID_BOOK_ID")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `update book - bad request - progressPercent less than 0`() {
        Request(Method.PATCH, "/api/v1/books/9999")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .with(updateBookPatchLens of UpdateBookPatch(progressPercent = -1))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `update book - bad request - progressPercent greater than 100`() {
        Request(Method.PATCH, "/api/v1/books/9999")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .with(updateBookPatchLens of UpdateBookPatch(progressPercent = 101))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `update book - bad request - empty request body`() {
        Request(Method.PATCH, "/api/v1/books/9999")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .with(updateBookPatchLens of UpdateBookPatch(null, null, null))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `update book - bad request - invalid status`() {
        Request(Method.PATCH, "/api/v1/books/9999")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .body("""{"status": "INVALID_STATUS"}""")
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `update book - success - progressPercent updated`() {
        val response =
            Request(Method.PATCH, "/api/v1/books/1")
                .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
                .with(updateBookPatchLens of UpdateBookPatch(10, null, null))
                .let(app)

        response shouldHaveStatus Status.OK

        val responseBody = bookResponseLens(response)
        responseBody.id shouldBe 1
        responseBody.progressPercent shouldBe 10
        responseBody.status shouldBe "TO_READ"
        responseBody.updatedAt shouldNotBe null
        responseBody.finishedAt shouldBe null
    }

    @Test
    fun `update book - success - status updated to FINISHED`() {
        val response =
            Request(Method.PATCH, "/api/v1/books/1")
                .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
                .with(updateBookPatchLens of UpdateBookPatch(100, ReadingStatus.FINISHED, null))
                .let(app)

        response shouldHaveStatus Status.OK

        val responseBody = bookResponseLens(response)
        responseBody.id shouldBe 1
        responseBody.progressPercent shouldBe 100
        responseBody.status shouldBe "FINISHED"
        responseBody.updatedAt shouldNotBe null
        responseBody.finishedAt shouldNotBe null
    }

    @Test
    fun `update book - success - move shelf`() {
        val response =
            Request(Method.PATCH, "/api/v1/books/1")
                .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
                .with(updateBookPatchLens of UpdateBookPatch(null, null, 2))
                .let(app)

        response shouldHaveStatus Status.OK
    }

    @Test
    fun `delete book - unauthorized - no token`() {
        Request(Method.DELETE, "/api/v1/books/999").let(app).shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete book - unauthorized - bad token`() {
        Request(Method.DELETE, "/api/v1/books/999")
            .cookie(Cookie("access_token", "foo"))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete book - invalid book id`() {
        Request(Method.DELETE, "/api/v1/books/INVALID_BOOK_ID")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
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
        Request(Method.DELETE, "/api/v1/books/1")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(2)))
            .let(app)
            .shouldHaveStatus(Status.FORBIDDEN)
    }

    @Test
    fun `delete book - success`() {
        val response =
            app(
                Request(Method.DELETE, "/api/v1/books/1")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            )

        response shouldHaveStatus Status.NO_CONTENT
        transaction { Books.selectAll().count() shouldBe 2 }
    }
}
