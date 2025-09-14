package integration

import dev.benkelenski.booked.auth.JwtTokenProvider
import dev.benkelenski.booked.createApp
import dev.benkelenski.booked.domain.requests.LoginRequest
import dev.benkelenski.booked.domain.requests.RegisterRequest
import dev.benkelenski.booked.http.loginReqLens
import dev.benkelenski.booked.http.registerReqLens
import dev.benkelenski.booked.http.userResLens
import dev.benkelenski.booked.loadConfig
import dev.benkelenski.booked.models.Shelves
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.http4k.base64Encode
import org.http4k.core.*
import org.http4k.core.cookie.SameSite
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.cookies
import org.http4k.kotest.shouldHaveStatus
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.reverseProxy
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import testUtils.TestDbUtils
import testUtils.fakeGoogleBooks
import java.security.KeyPairGenerator
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthIntegrationTest {
    private fun Response.cookie(name: String) = cookies().firstOrNull { it.name == name }

    private val jwtRegex = Regex("^[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+$")

    private lateinit var postgres: PostgreSQLContainer<*>

    private lateinit var app: RoutingHttpHandler

    private val keyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private val config = loadConfig("test")

    private val fakeTokenProvider = JwtTokenProvider()

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
    fun `create user - bad request - no request body`() {
        Request(Method.POST, "/api/v1/auth/register").let(app).shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `create user - request validation error - blank email`() {
        Request(Method.POST, "/api/v1/auth/register")
            .with(
                Body.registerReqLens of
                    RegisterRequest(
                        email = " ",
                        password = "sUp3rs3curep@s$123",
                        name = "test user",
                    )
            )
            .let(app)
            .shouldHaveStatus(Status.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `create user - request validation error - blank password`() {
        Request(Method.POST, "/api/v1/auth/register")
            .with(
                Body.registerReqLens of
                    RegisterRequest(
                        email = "test@test.com",
                        password = "   ",
                        name = "test user",
                    )
            )
            .let(app)
            .shouldHaveStatus(Status.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `create user - validation error - reserved display name`() {
        Request(Method.POST, "/api/v1/auth/register")
            .with(
                Body.registerReqLens of
                    RegisterRequest(
                        email = "test@test.com",
                        password = "sUp3rs3curep@s$123",
                        name = "admin",
                    )
            )
            .let(app)
            .shouldHaveStatus(Status.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `create new user - success`() {
        val response =
            Request(Method.POST, "/api/v1/auth/register")
                .with(
                    Body.registerReqLens of
                        RegisterRequest(
                            email = "test@test.com",
                            password = "sUp3rs3curep@s$123",
                            name = "Test User",
                        )
                )
                .let(app)

        response shouldHaveStatus Status.OK

        testResponseTokens(response)

        val responseBody = Body.userResLens(response)

        responseBody.id shouldBe 1
        responseBody.name shouldBe "Test User"
        responseBody.email shouldBe "test@test.com"

        transaction {
            Shelves.selectAll()
                .where { (Shelves.userId eq 1) and (Shelves.isDeletable eq false) }
                .count() shouldBe 3
        }
    }

    @Test
    fun `login user - bad request - no request body`() {
        Request(Method.POST, "/api/v1/auth/login").let(app).shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `login user - bad request - email empty`() {
        Request(Method.POST, "/api/v1/auth/login")
            .with(Body.loginReqLens of LoginRequest(email = " ", password = "123456"))
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `login user - bad request - invalid email`() {
        Request(Method.POST, "/api/v1/auth/login")
            .with(Body.loginReqLens of LoginRequest(email = "test", password = "123456"))
            .let(app)
    }

    @Test
    fun `login user - bad request - password empty`() {
        Request(Method.POST, "/api/v1/auth/login")
            .with(
                Body.loginReqLens of
                    LoginRequest(email = "test@test.com", password = " \t\t\t\r\r\r")
            )
            .let(app)
            .shouldHaveStatus(Status.BAD_REQUEST)
    }

    @Test
    fun `login user - success`() {
        TestDbUtils.createEmailUser("test@test.com", "sUp3rs3curep@s$123", "Test User")

        val response =
            Request(Method.POST, "/api/v1/auth/login")
                .with(
                    Body.loginReqLens of
                        LoginRequest(email = "test@test.com", password = "sUp3rs3curep@s$123")
                )
                .let(app)

        response shouldHaveStatus Status.OK

        testResponseTokens(response)

        val responseBody = Body.userResLens(response)

        responseBody.email shouldBe "test@test.com"
        responseBody.name shouldBe "Test User"
    }

    @Test
    fun `logout user - missing access token`() {
        Request(Method.POST, "/api/v1/auth/logout").let(app).shouldHaveStatus(Status.UNAUTHORIZED)
    }

    @Test
    fun `logout user - success`() {
        TestDbUtils.createEmailUser("test@test.com", "sUp3rs3curep@s$123", "Test User")

        val response =
            Request(Method.POST, "/api/v1/auth/logout")
                .cookie("access_token", fakeTokenProvider.generateAccessToken(1))
                .let(app)

        response shouldHaveStatus Status.OK
        response.header("Set-Cookie") shouldContain "access_token=\"\"; Max-Age=0; Path=/;"
    }

    @Test
    fun `oauth user - bad request - no request body`() {
        Request(Method.POST, "/api/v1/auth/oauth").let(app).shouldHaveStatus(Status.BAD_REQUEST)
    }

    // TODO: Get Oauth test to work

    //    @Test
    //    fun `oauth user - success`() {
    //        val publicKey = config.server.auth.google.publicKey
    //        val keySpec = X509EncodedKeySpec(publicKey!!.base64DecodedArray())
    //        val javaPublicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)
    //
    //        val jwt =
    //            JWT.create()
    //                .withSubject("google-user-id")
    //                .withClaim("email", "user@example.com")
    //                .withIssuer("https://accounts.google.com")
    //                .sign(Algorithm.RSA256(javaPublicKey as RSAPublicKey, null))
    //
    //        val response =
    //            Request(Method.POST, "/api/v1/auth/oauth")
    //                .with(authRequestLens of OAuthRequest(provider = "google", token = jwt))
    //                .let(app)
    //
    //        response shouldHaveStatus Status.OK
    //    }

    private fun testResponseTokens(response: Response) {
        val access = response.cookie("access_token") ?: fail("missing access_token")
        val refresh = response.cookie("refresh_token") ?: fail("missing refresh_token")

        jwtRegex.matches(access.value) shouldBe true
        jwtRegex.matches(refresh.value) shouldBe true

        access.httpOnly shouldBe true
        refresh.httpOnly shouldBe true

        access.secure shouldBe true
        refresh.secure shouldBe true

        access.sameSite shouldBe SameSite.Strict
        refresh.sameSite shouldBe SameSite.Strict

        access.path shouldBe "/"
        refresh.path shouldBe "/auth/refresh"

        access.maxAge.shouldNotBeNull()
        refresh.maxAge.shouldNotBeNull()

        assertTrue(access.maxAge!! in 800..1000)
        assertTrue(refresh.maxAge!! in 600000..700000)
    }
}
