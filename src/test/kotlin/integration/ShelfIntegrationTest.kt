package integration

import dev.benkelenski.booked.createApp
import dev.benkelenski.booked.domain.requests.BookRequest
import dev.benkelenski.booked.domain.requests.ShelfRequest
import dev.benkelenski.booked.domain.responses.BookResponse
import dev.benkelenski.booked.domain.responses.ShelfResponse
import dev.benkelenski.booked.loadConfig
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import dev.benkelenski.booked.repos.UserRepo
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
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import utils.FakeTokenProvider
import utils.TestDbUtils
import utils.fakeGoogleBooks
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
        val user =
            UserRepo()
                .getOrCreateUser(
                    provider = "email",
                    providerUserId = "test@test.com",
                    email = "test@test.com",
                    name = "testuser",
                    password = "securepass",
                )

        repeat(3) { ShelfRepo().addShelf(userId = user.id, name = "shelf $it", description = null) }

        val response =
            app(
                Request(Method.GET, "/api/v1/shelves")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(user.id)))
            )

        response shouldHaveStatus Status.OK
        val responseBody = shelvesResLens(response)
        responseBody shouldHaveSize 3
    }

    @Test
    fun `get all shelves - none found`() {
        val user =
            UserRepo()
                .getOrCreateUser(
                    provider = "email",
                    providerUserId = "test@test.com",
                    email = "test@test.com",
                    name = "testuser",
                    password = "securepass",
                )

        val response =
            app(
                Request(Method.GET, "/api/v1/shelves")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(user.id)))
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
        val user =
            UserRepo()
                .getOrCreateUser(
                    provider = "email",
                    providerUserId = "test@test.com",
                    email = "test@test.com",
                    name = "testuser",
                    password = "securepass",
                )

        Request(Method.GET, "/v1/shelves/9999")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(user.id)))
            .let(app)
            .shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `get shelf - success`() {
        val user =
            UserRepo()
                .getOrCreateUser(
                    provider = "email",
                    providerUserId = "test@test.com",
                    email = "test@test.com",
                    name = "testuser",
                    password = "securepass",
                )

        val shelf = ShelfRepo().addShelf(userId = user.id, name = "shelf 1", description = null)
        ShelfRepo().addShelf(userId = 1, name = "shelf 2", description = null)
        ShelfRepo().addShelf(userId = 1, name = "shelf 3", description = null)

        val expectedShelfRes = ShelfResponse.from(shelf!!)

        val response =
            app(
                Request(Method.GET, "/api/v1/shelves/${shelf.id}")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(user.id)))
            )

        response shouldHaveStatus Status.OK
        val responseBody = shelfResLens(response)
        responseBody shouldBe expectedShelfRes
        responseBody.name shouldBe expectedShelfRes.name
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
        val user =
            UserRepo()
                .getOrCreateUser(
                    provider = "email",
                    providerUserId = "test@test.com",
                    email = "test@test.com",
                    name = "testuser",
                    password = "securepass",
                )

        val response =
            app(
                Request(Method.POST, "/api/v1/shelves")
                    .with(shelfRequestLens of ShelfRequest("shelf 1", null))
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(user.id)))
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
        val user =
            UserRepo()
                .getOrCreateUser(
                    provider = "email",
                    providerUserId = "test@test.com",
                    email = "test@test.com",
                    name = "testuser",
                    password = "securepass",
                )

        val shelf = ShelfRepo().addShelf(userId = user.id, name = "shelf 1", description = null)

        repeat(3) {
            BookRepo()
                .saveBook(
                    userId = user.id,
                    googleId = "google$it",
                    title = "test book $it",
                    authors = listOf("test author $it"),
                    shelfId = shelf!!.id,
                    thumbnailUrl = null,
                )
        }

        val response =
            app(
                Request(Method.GET, "/api/v1/shelves/${shelf?.id}/books")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(user.id)))
            )

        response.status shouldBe Status.OK

        val books = booksResponseLens(response)

        books.size shouldBe 3
        books.forEachIndexed { index, book ->
            book.id shouldBe index.plus(1)
            book.title shouldBe "test book $index"
            book.authors shouldBe listOf("test author $index")
            book.googleId shouldBe "google$index"
            book.thumbnailUrl shouldBe null
            book.createdAt shouldNotBe null
        }
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
    fun `add book to shelf - success`() {
        val user =
            UserRepo()
                .getOrCreateUser(
                    provider = "email",
                    providerUserId = "test@test.com",
                    email = "test@test.com",
                    name = "testuser",
                    password = "securepass",
                )

        val shelf = ShelfRepo().addShelf(userId = user.id, name = "shelf 1", description = null)

        val googleBookId = "google1"

        val response =
            app(
                Request(Method.POST, "/api/v1/shelves/${shelf?.id}/books")
                    .with(bookRequestLens of BookRequest(googleBookId))
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(user.id)))
            )

        val book = bookResponseLens(response)
        book.id shouldBe 1
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
        UserRepo()
            .getOrCreateUser(
                provider = "email",
                providerUserId = "test@test.com",
                email = "test@test.com",
                name = "testuser",
                password = "securepass",
            )

        val shelf = ShelfRepo().addShelf(userId = 1, name = "shelf 1", description = null)

        Request(Method.DELETE, "/api/v1/shelves/${shelf?.id}")
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
        val user =
            UserRepo()
                .getOrCreateUser(
                    provider = "email",
                    providerUserId = "test@test.com",
                    email = "test@test.com",
                    name = "testuser",
                    password = "securepass",
                )

        val shelf = ShelfRepo().addShelf(userId = user.id, name = "shelf 1", description = null)
        ShelfRepo().addShelf(userId = user.id, name = "shelf 2", description = null)

        Request(Method.DELETE, "/api/v1/shelves/${shelf?.id}")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(user.id)))
            .let(app)
            .shouldHaveStatus(Status.NO_CONTENT)

        ShelfRepo().getAllShelves(userId = user.id) shouldHaveSize 1
    }
}
