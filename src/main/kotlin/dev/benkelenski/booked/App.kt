package dev.benkelenski.booked

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import dev.benkelenski.booked.clients.GoogleBooksClient
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import dev.benkelenski.booked.routes.bookRoutes
import dev.benkelenski.booked.routes.shelfRoutes
import dev.benkelenski.booked.services.BookService
import dev.benkelenski.booked.services.ShelfService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.base64DecodedArray
import org.http4k.client.OkHttp
import org.http4k.config.Environment
import org.http4k.config.EnvironmentKey
import org.http4k.core.HttpHandler
import org.http4k.core.Uri
import org.http4k.lens.base64
import org.http4k.lens.secret
import org.http4k.lens.string
import org.http4k.lens.uri
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.jetbrains.exposed.sql.Database
import java.net.URI
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit

val dbUrl = EnvironmentKey.string().required("DB_URL")
val dbUser = EnvironmentKey.string().required("DB_USER")
val dbPass = EnvironmentKey.secret().required("DB_PASS")
val publicKey = EnvironmentKey.base64().optional("PUBLIC_KEY")
val jwksUri = EnvironmentKey.uri().required("JWKS_URI")
val issuer = EnvironmentKey.string().required("ISSUER")
val audience = EnvironmentKey.string().required("AUDIENCE")
val redirectUri = EnvironmentKey.uri().required("REDIRECT_URI")
val googleApisHost =
    EnvironmentKey.uri().defaulted("GOOGLE_APIS_HOST", Uri.of("https://www.googleapis.com"))
val googleApisKey = EnvironmentKey.secret().required("GOOGLE_APIS_KEY")

val logger = KotlinLogging.logger {}

private fun withPrefix(prefix: String, vararg routes: RoutingHttpHandler): RoutingHttpHandler {
    return prefix bind routes(*routes)
}

private fun createDbConn(env: Environment) {
    Database.connect(
        url = env[dbUrl],
        driver = "org.postgresql.Driver",
        user = env[dbUser],
        password = env[dbPass].use { it },
    )
}

fun createApp(env: Environment, internet: HttpHandler): RoutingHttpHandler {
    val algorithm =
        env[publicKey]?.let { publicKey ->
            val keySpec = X509EncodedKeySpec(publicKey.base64DecodedArray())
            val javaPublicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)
            Algorithm.RSA256(javaPublicKey as RSAPublicKey, null)
        }
            ?: run {
                val provider =
                    JwkProviderBuilder(URI.create(env[jwksUri].toString()).toURL())
                        .cached(10, 24, TimeUnit.HOURS)
                        .rateLimited(10, 1, TimeUnit.MINUTES)
                        .build()

                val rsaKeyProvider =
                    object : RSAKeyProvider {
                        override fun getPublicKeyById(keyId: String?) =
                            provider.get(keyId).publicKey as RSAPublicKey

                        override fun getPrivateKey() = null

                        override fun getPrivateKeyId() = null
                    }

                Algorithm.RSA256(rsaKeyProvider)
            }

    val verifier =
        JWT.require(algorithm).withIssuer(env[issuer]).withAudience(env[audience]).build()

    val bookService =
        BookService(
            bookRepo = BookRepo(),
            jwtVerifier = verifier,
            googleBooksClient =
                GoogleBooksClient(
                    host = env[googleApisHost],
                    apiKey = env[googleApisKey].use { it },
                    internet = internet,
                ),
        )

    val shelfService = ShelfService(shelfRepo = ShelfRepo())

    return withPrefix(
        "/api/v1",
        bookRoutes(
            bookService::getBook,
            bookService::getAllBooks,
            bookService::createBook,
            bookService::deleteBook,
            bookService::searchBooks,
            bookService::verify,
        ),
        shelfRoutes(
            shelfService::getShelf,
            shelfService::getAllShelves,
            shelfService::createShelf,
            shelfService::deleteShelf,
        ),
    )
}

fun main() {
    val port = 8080
    val env = Environment.ENV

    logger.info { "creating database connection" }
    createDbConn(env = env)
    logger.info { "creating app" }
    val app = createApp(env = env, internet = OkHttp())
    val webApp = webApp(env[audience], env[redirectUri])

    logger.info { "starting app on port: $port" }
    routes(app, webApp).asServer(Jetty(port)).start()
}
