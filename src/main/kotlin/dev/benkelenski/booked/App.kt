package dev.benkelenski.booked

import dev.benkelenski.booked.repos.BooksRepo
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
import org.http4k.routing.routes
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.jetbrains.exposed.sql.Database

val dbUrl = EnvironmentKey.string().required("DB_URL")
val dbUser = EnvironmentKey.string().required("DB_USER")
val dbPass = EnvironmentKey.secret().required("DB_PASS")

val logger = KotlinLogging.logger {}

fun createApp(env: Environment): RoutingHttpHandler {
    Database.connect(
        url = env[dbUrl],
        driver = "org.postgresql.Driver",
        user = env[dbUser],
        password = env[dbPass].use { it }
    )

    val bookService = BookService(
        booksRepo = BooksRepo()
    )

    val shelfService = ShelfService(
        shelfRepo = ShelfRepo()
    )

    return routes(
        bookRoutes(bookService),
        shelfRoutes(shelfService)
    )
}

fun main() {
    val port = 8080
    logger.info { "creating app" }
    val routingHandler = createApp(env = Environment.ENV)
    logger.info { "starting app on port: $port" }
    routingHandler
        .asServer(Jetty(port))
        .start()
}