package dev.benkelenski.booked

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addResourceSource
import dev.benkelenski.booked.auth.AuthProvider
import dev.benkelenski.booked.clients.GoogleBooksClient
import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import dev.benkelenski.booked.routes.bookRoutes
import dev.benkelenski.booked.routes.shelfRoutes
import dev.benkelenski.booked.services.BookService
import dev.benkelenski.booked.services.ShelfService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.client.OkHttp
import org.http4k.core.HttpHandler
import org.http4k.core.Uri
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.jetbrains.exposed.sql.Database

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

    val authProvider =
        AuthProvider(
            publicKey = config.server.auth.publicKey,
            jwksUri = config.server.auth.jwksUri,
            issuer = config.server.auth.issuer,
            audience = config.server.auth.audience,
        )

    val bookService =
        BookService(
            bookRepo = BookRepo(),
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
            authProvider::verify,
        ),
        shelfRoutes(
            shelfService::getShelf,
            shelfService::getAllShelves,
            shelfService::createShelf,
            shelfService::deleteShelf,
            authProvider::verify,
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
