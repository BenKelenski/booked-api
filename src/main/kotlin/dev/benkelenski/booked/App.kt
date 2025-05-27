package dev.benkelenski.booked

import dev.benkelenski.booked.repos.BookRepo
import dev.benkelenski.booked.repos.ShelfRepo
import dev.benkelenski.booked.routes.bookRoutes
import dev.benkelenski.booked.routes.shelfRoutes
import dev.benkelenski.booked.services.BookService
import dev.benkelenski.booked.services.ShelfService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.config.Environment
import org.http4k.config.EnvironmentKey
import org.http4k.lens.secret
import org.http4k.lens.string
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.jetbrains.exposed.sql.Database

val dbUrl = EnvironmentKey.string().required("DB_URL")
val dbUser = EnvironmentKey.string().required("DB_USER")
val dbPass = EnvironmentKey.secret().required("DB_PASS")

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

fun createApp(): RoutingHttpHandler {
    val bookService = BookService(bookRepo = BookRepo())

    val shelfService = ShelfService(shelfRepo = ShelfRepo())

    return withPrefix(
        "/api/v1",
        bookRoutes(
            bookService::getBook,
            bookService::getAllBooks,
            bookService::createBook,
            bookService::deleteBook,
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
    logger.info { "creating database connection" }
    createDbConn(env = Environment.ENV)
    logger.info { "creating app" }
    val app = createApp()
    logger.info { "starting app on port: $port" }
    app.asServer(Jetty(port)).start()
}
