package dev.benkelenski.booked

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceSource
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
import org.http4k.core.HttpHandler
import org.http4k.core.Uri
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

val logger = KotlinLogging.logger {}

private fun withPrefix(prefix: String, vararg routes: RoutingHttpHandler): RoutingHttpHandler {
    return prefix bind routes(*routes)
}

fun loadConfig(profile: String? = null): Config {
    val loader = ConfigLoaderBuilder.default()

    return if (profile != null) {
        loader.addResourceSource("/application-$profile.conf").build().loadConfigOrThrow<Config>()
    } else {
        loader.addResourceSource("/application.conf").build().loadConfigOrThrow<Config>()
    }
}

fun createDbConn(config: Config) {
    Database.connect(
        url = config.database.url,
        driver = config.database.driver,
        user = config.database.user,
        password = config.database.password,
    )
}

fun createApp(config: Config, internet: HttpHandler): RoutingHttpHandler {
    val algorithm =
        config.server.auth.publicKey?.let { publicKey ->
            val keySpec = X509EncodedKeySpec(publicKey.base64DecodedArray())
            val javaPublicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)
            Algorithm.RSA256(javaPublicKey as RSAPublicKey, null)
        }
            ?: run {
                val provider =
                    JwkProviderBuilder(URI.create(config.server.auth.jwksUri).toURL())
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
        JWT.require(algorithm)
            .withIssuer(config.server.auth.issuer)
            .withAudience(config.server.auth.audience)
            .build()

    val bookService =
        BookService(
            bookRepo = BookRepo(),
            jwtVerifier = verifier,
            googleBooksClient =
                GoogleBooksClient(
                    host = config.client.googleApisHost.toUri(),
                    apiKey = config.client.googleApisKey,
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
    val config = loadConfig()

    logger.info { "creating database connection" }
    createDbConn(config = config)
    logger.info { "creating app" }
    val app = createApp(config = config, internet = OkHttp())
    val webApp = webApp(config.server.auth.audience, config.server.auth.redirectUri.toUri())

    logger.info { "starting app on port: ${config.server.port}" }
    routes(app, webApp).asServer(Jetty(config.server.port)).start()
}

fun String.toUri(): Uri = Uri.of(this)
