package integration

import TestUtils
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.benkelenski.booked.createApp
import dev.benkelenski.booked.domain.ShelfRequest
import dev.benkelenski.booked.loadConfig
import dev.benkelenski.booked.repos.ShelfRepo
import dev.benkelenski.booked.routes.shelfLens
import dev.benkelenski.booked.routes.shelfRequestLens
import dev.benkelenski.booked.routes.shelvesLens
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.http4k.base64Encode
import org.http4k.core.*
import org.http4k.kotest.shouldHaveStatus
import org.http4k.lens.bearerAuth
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.reverseProxy
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShelfIntegrationTest {

    private lateinit var postgres: PostgreSQLContainer<*>

    private lateinit var app: RoutingHttpHandler

    private val keyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private val config = loadConfig("test")

    private fun createToken(userId: String): String {
        val algorithm = Algorithm.RSA256(null, keyPair.private as RSAPrivateKey)
        return JWT.create()
            .withIssuer(config.server.auth.issuer)
            .withAudience(config.server.auth.audience)
            .withSubject(userId)
            .sign(algorithm)
    }

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

        config.apply { server.auth.publicKey = keyPair.public.encoded.base64Encode() }
        app =
            createApp(
                config = config,
                internet =
                    reverseProxy(Uri.of(config.client.googleApisHost).host to fakeGoogleBooks()),
            )
    }

    @BeforeEach
    fun setup() {
        TestUtils.buildTables()
    }

    @AfterEach
    fun teardown() {
        TestUtils.dropTables()
    }

    @AfterAll
    fun teardownDb() {
        postgres.stop()
    }

    @Test
    fun `get all shelves`() {
        repeat(3) { ShelfRepo().addShelf(userId = "user1", name = "shelf $it", description = null) }

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
        val shelf = ShelfRepo().addShelf(userId = "user1", name = "shelf 1", description = null)
        ShelfRepo().addShelf(userId = "user1", name = "shelf 2", description = null)
        ShelfRepo().addShelf(userId = "user1", name = "shelf 3", description = null)

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
        val response =
            app(
                Request(Method.POST, "/api/v1/shelves")
                    .with(shelfRequestLens of ShelfRequest("shelf 1", null))
                    .bearerAuth(createToken("user1"))
            )

        response shouldHaveStatus Status.CREATED
        response.bodyString() shouldContain "shelf 1"
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
            .bearerAuth(createToken("user1"))
            .let(app)
            .shouldHaveStatus(Status.NOT_FOUND)
    }

    @Test
    fun `delete shelf - forbidden`() {
        val shelf = ShelfRepo().addShelf(userId = "user1", name = "shelf 1", description = null)

        Request(Method.DELETE, "/api/v1/shelves/${shelf?.id}")
            .bearerAuth(createToken("user2"))
            .let(app)
            .shouldHaveStatus(Status.FORBIDDEN)
    }

    @Test
    fun `delete shelf - success`() {
        val shelf = ShelfRepo().addShelf(userId = "user1", name = "shelf 1", description = null)
        ShelfRepo().addShelf(userId = "user1", name = "shelf 2", description = null)

        Request(Method.DELETE, "/api/v1/shelves/${shelf?.id}")
            .bearerAuth(createToken("user1"))
            .let(app)
            .shouldHaveStatus(Status.NO_CONTENT)

        ShelfRepo().getAllShelves() shouldHaveSize 1
    }
}
