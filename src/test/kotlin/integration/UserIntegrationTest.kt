package integration

import dev.benkelenski.booked.constants.HttpConstants
import dev.benkelenski.booked.createApp
import dev.benkelenski.booked.http.userResLens
import dev.benkelenski.booked.loadConfig
import io.kotest.matchers.shouldBe
import org.http4k.base64Encode
import org.http4k.core.*
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.kotest.shouldHaveStatus
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.reverseProxy
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import testUtils.FakeTokenProvider
import testUtils.TestDbUtils
import testUtils.fakeGoogleBooks
import java.security.KeyPairGenerator

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserIntegrationTest {

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
    fun `get me - unauthorized - no token`() {
        Request(Method.GET, "/api/v1/users/me").let(app).shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `get me - unauthorized - invalid token`() {
        Request(Method.GET, "/api/v1/users/me")
            .cookie(Cookie(HttpConstants.ACCESS_TOKEN_COOKIE, "foo"))
            .let(app)
            .shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `get me - success`() {
        val response =
            app(
                Request(Method.GET, "/api/v1/users/me")
                    .cookie(
                        Cookie(
                            HttpConstants.ACCESS_TOKEN_COOKIE,
                            fakeTokenProvider.generateAccessToken(1),
                        )
                    )
            )

        response shouldHaveStatus Status.OK
        val responseBody = Body.userResLens(response)
        responseBody.id shouldBe 1
        responseBody.email shouldBe "test@test.com"
        responseBody.name shouldBe "testuser"
    }
}
