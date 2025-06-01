package dev.benkelenski.booked

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import dev.benkelenski.booked.routes.bookRoutes
import dev.benkelenski.booked.routes.shelfRoutes
import dev.benkelenski.booked.services.BookService
import dev.benkelenski.booked.services.ShelfService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.base64DecodedArray
import org.http4k.config.Environment
import org.http4k.config.EnvironmentKey
import org.http4k.lens.base64
import org.http4k.lens.secret
import org.http4k.lens.string
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.jetbrains.exposed.sql.Database
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec

val dbUrl = EnvironmentKey.string().required("DB_URL")
val dbUser = EnvironmentKey.string().required("DB_USER")
val dbPass = EnvironmentKey.secret().required("DB_PASS")
val publicKey = EnvironmentKey.base64().required("PUBLIC_KEY")
val issuer = EnvironmentKey.string().required("ISSUER")
val audience = EnvironmentKey.string().required("AUDIENCE")

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

fun createApp(env: Environment): RoutingHttpHandler {
    val keySpec = X509EncodedKeySpec(env[publicKey].base64DecodedArray())
    val publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)
    val algorithm = Algorithm.RSA256(publicKey as RSAPublicKey, null)

    val verifier =
        JWT.require(algorithm).withIssuer(env[issuer]).withAudience(env[audience]).build()

    val bookService = BookService(bookRepo = BookRepo(), jwtVerifier = verifier)

    val shelfService = ShelfService(shelfRepo = ShelfRepo())

    return withPrefix(
        "/api/v1",
        bookRoutes(
            bookService::getBook,
            bookService::getAllBooks,
            bookService::createBook,
            bookService::deleteBook,
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
    val app = createApp(env = env)
    logger.info { "starting app on port: $port" }
    app.asServer(Jetty(port)).start()
}
