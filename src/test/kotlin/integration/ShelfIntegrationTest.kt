package integration

import dev.benkelenski.booked.createApp
import dev.benkelenski.booked.domain.ShelfType
import dev.benkelenski.booked.domain.requests.ShelfRequest
import dev.benkelenski.booked.domain.shelfReqLens
import dev.benkelenski.booked.domain.shelfResLens
import dev.benkelenski.booked.domain.shelvesResLens
import dev.benkelenski.booked.loadConfig
import dev.benkelenski.booked.models.Shelves
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
    fun `get all shelves - success`() {
        val response =
            app(
                Request(Method.GET, "/api/v1/shelves")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            )

        response shouldHaveStatus Status.OK
        val responseBody = Body.shelvesResLens(response).sortedBy { it.id }
        responseBody shouldHaveSize 4
        responseBody[0].bookCount shouldBe 3
        responseBody[1].bookCount shouldBe 1
        responseBody[2].bookCount shouldBe 0
    }

    @Test
    fun `get all shelves - none found`() {

        val response =
            app(
                Request(Method.GET, "/api/v1/shelves")
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(2)))
            )

        response shouldHaveStatus Status.OK
        val responseBody = Body.shelvesResLens(response)
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
        val responseBody = Body.shelfResLens(response)
        responseBody.name shouldBe "To Read"
        responseBody.bookCount shouldBe 3
    }

    @Test
    fun `create shelf - unauthorized - no token`() {
        Request(Method.POST, "/api/v1/shelves")
            .with(Body.shelfReqLens of ShelfRequest("shelf 1", null))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `create shelf - unauthorized - bad token`() {
        Request(Method.POST, "/api/v1/shelves")
            .cookie(Cookie("access_token", "foo"))
            .with(Body.shelfReqLens of ShelfRequest("shelf 1", null))
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
            .with(Body.shelfReqLens of ShelfRequest("", null))
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `create shelf - success`() {
        val response =
            app(
                Request(Method.POST, "/api/v1/shelves")
                    .with(Body.shelfReqLens of ShelfRequest("shelf 1", null))
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            )

        val responseBody = Body.shelfResLens(response)

        response shouldHaveStatus Status.CREATED
        responseBody.name shouldBe "shelf 1"
        responseBody.description shouldBe null
        responseBody.createdAt shouldNotBe null
        responseBody.shelfType shouldBe ShelfType.CUSTOM
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
        Request(Method.DELETE, "/api/v1/shelves/4")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(1)))
            .let(app)
            .shouldHaveStatus(Status.NO_CONTENT)

        transaction { Shelves.selectAll().where { Shelves.userId eq 1 }.count() shouldBe 3 }
    }
}
