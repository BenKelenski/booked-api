package integration

import dev.benkelenski.booked.createApp
import dev.benkelenski.booked.domain.ShelfRequest
import dev.benkelenski.booked.loadConfig
import dev.benkelenski.booked.repos.ShelfRepo
import dev.benkelenski.booked.routes.shelfLens
import dev.benkelenski.booked.routes.shelfRequestLens
import dev.benkelenski.booked.routes.shelvesLens
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
import utils.FakeDbUtils
import utils.FakeTokenProvider
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
    fun `get all shelves`() {
        repeat(3) { ShelfRepo().addShelf(userId = 1, name = "shelf $it", description = null) }

        val response = app(Request(Method.GET, "/api/v1/shelves"))

        response shouldHaveStatus Status.OK
        val responseBody = shelvesLens(response)
        responseBody shouldHaveSize 3
    }

    @Test
    fun `get all shelves - none found`() {
        val response = app(Request(Method.GET, "/api/v1/shelves"))

        response shouldHaveStatus Status.OK
        val responseBody = shelvesLens(response)
        responseBody shouldHaveSize 0
    }

    @Test
    fun `get shelf - not found`() {
        Request(Method.GET, "/v1/shelves/9999").let(app).shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `get shelf - found`() {
        val shelf = ShelfRepo().addShelf(userId = 1, name = "shelf 1", description = null)
        ShelfRepo().addShelf(userId = 1, name = "shelf 2", description = null)
        ShelfRepo().addShelf(userId = 1, name = "shelf 3", description = null)

        val response = app(Request(Method.GET, "/api/v1/shelves/${shelf?.id}"))

        response shouldHaveStatus Status.OK
        val responseBody = shelfLens(response)
        responseBody shouldBe shelf
        responseBody.name shouldBe shelf?.name
    }

    @Test
    fun `create shelf - unauthorized`() {
        Request(Method.POST, "/api/v1/shelves")
            .with(shelfRequestLens of ShelfRequest("shelf 1", null))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `create shelf`() {
        val userId = 1
        val response =
            app(
                Request(Method.POST, "/api/v1/shelves")
                    .with(shelfRequestLens of ShelfRequest("shelf 1", null))
                    .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(userId)))
            )

        val responseBody = shelfLens(response)

        response shouldHaveStatus Status.CREATED
        responseBody.id shouldBe 1
        responseBody.name shouldBe "shelf 1"
        responseBody.description shouldBe null
        responseBody.createdAt shouldNotBe null
        responseBody.userId shouldBe userId
    }

    @Test
    fun `delete shelf - unauthorized due to no token`() {
        Request(Method.DELETE, "/api/v1/shelves/999").let(app).shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `delete shelf - unauthorized due to bad token`() {
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
        val shelf = ShelfRepo().addShelf(userId = 1, name = "shelf 1", description = null)

        Request(Method.DELETE, "/api/v1/shelves/${shelf?.id}")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(2)))
            .let(app)
            .shouldHaveStatus(Status.FORBIDDEN)
    }

    @Test
    fun `delete shelf - success`() {
        val userId = 1

        val shelf = ShelfRepo().addShelf(userId = userId, name = "shelf 1", description = null)
        ShelfRepo().addShelf(userId = userId, name = "shelf 2", description = null)

        Request(Method.DELETE, "/api/v1/shelves/${shelf?.id}")
            .cookie(Cookie("access_token", fakeTokenProvider.generateAccessToken(userId)))
            .let(app)
            .shouldHaveStatus(Status.NO_CONTENT)

        ShelfRepo().getAllShelves() shouldHaveSize 1
    }
}
