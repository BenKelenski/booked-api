package integration

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.benkelenski.booked.*
import dev.benkelenski.booked.models.BookRequest
import dev.benkelenski.booked.models.BookTable
import dev.benkelenski.booked.models.ShelfTable
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import dev.benkelenski.booked.routes.bookLens
import dev.benkelenski.booked.routes.bookRequestLens
import dev.benkelenski.booked.routes.booksLens
import io.kotest.matchers.be
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.http4k.base64Encode
import org.http4k.config.Environment
import org.http4k.config.Secret
import org.http4k.core.*
import org.http4k.kotest.shouldHaveBody
import org.http4k.kotest.shouldHaveStatus
import org.http4k.lens.bearerAuth
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.reverseProxy
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey

private const val ISSUER = "booked_idp"
private const val AUDIENCE = "booked_app"
private val GOOGLE_BOOKS_API_HOST = Uri.of("https://www.googleapis.test")
private val GOOGLE_APIS_KEY = Secret("FAKE_API_KEY")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BookIntegrationTest {

    private lateinit var postgres: PostgreSQLContainer<*>

    private lateinit var app: RoutingHttpHandler

    private val keyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun createToken(userId: String): String {
        val algorithm = Algorithm.RSA256(null, keyPair.private as RSAPrivateKey)
        return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(userId)
            .sign(algorithm)
    }

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

        app =
            createApp(
                env =
                    Environment.defaults(
                        publicKey of keyPair.public.encoded.base64Encode(),
                        issuer of ISSUER,
                        audience of AUDIENCE,
                        googleApisHost of GOOGLE_BOOKS_API_HOST,
                        googleApisKey of GOOGLE_APIS_KEY,
                    ),
                internet = reverseProxy(GOOGLE_BOOKS_API_HOST.host to fakeGoogleBooks()),
            )
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
        val shelf = ShelfRepo().addShelf(name = "test", description = null)

        val book1 =
            BookRepo()
                .saveBook(
                    userId = "user1",
                    title = "test book 1",
                    author = "test author 1",
                    shelfId = shelf!!.id,
                )

        val book2 =
            BookRepo()
                .saveBook(
                    userId = "user1",
                    title = "test book 2",
                    author = "test author 2",
                    shelfId = shelf.id,
                )

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
        val shelf = ShelfRepo().addShelf(name = "test", description = null)

        val book1 =
            BookRepo()
                .saveBook(
                    userId = "user1",
                    title = "test book 1",
                    author = "test author 1",
                    shelfId = shelf!!.id,
                )

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
        val shelf = ShelfRepo().addShelf(name = "test", description = null)

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
                    .bearerAuth(createToken("user1"))
            )

        response shouldHaveStatus Status.CREATED
        response.bodyString() shouldContain "user1"
        response.bodyString() shouldContain "Red Rising"
        response.bodyString() shouldContain "Pierce Brown"
    }

    @Test
    fun `delete book - unauthorized due to no token`() {
        Request(Method.DELETE, "/api/v1/books/999").let(app).shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete book - unauthorized due to bad token`() {
        Request(Method.DELETE, "/api/v1/books/999")
            .bearerAuth("foo")
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete book - not found`() {
        Request(Method.DELETE, "/api/v1/books/999")
            .bearerAuth(createToken("user1"))
            .let(app)
            .shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `delete book - forbidden`() {
        val shelf = ShelfRepo().addShelf(name = "test", description = null)

        val book =
            BookRepo()
                .saveBook(
                    userId = "user2",
                    title = "test book 1",
                    author = "test author 1",
                    shelfId = shelf!!.id,
                )

        Request(Method.DELETE, "/api/v1/books/${book?.id}")
            .bearerAuth(createToken("user1"))
            .let(app)
            .shouldHaveStatus(Status.FORBIDDEN)
    }

    @Test
    fun `delete book - success`() {
        val shelf = ShelfRepo().addShelf(name = "test", description = null)

        BookRepo()
            .saveBook(
                userId = "user1",
                title = "test book 1",
                author = "test author 1",
                shelfId = shelf!!.id,
            )

        val book2 =
            BookRepo()
                .saveBook(userId = "user1", "test book 2", "test author 2", shelfId = shelf.id)

        val response =
            app(
                Request(Method.DELETE, "/api/v1/books/${book2?.id}")
                    .bearerAuth(createToken("user1"))
            )

        response shouldHaveStatus Status.NO_CONTENT
        BookRepo().getAllBooks() shouldHaveSize 1
    }
}
