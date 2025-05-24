package dev.benkelenski.booked

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.benkelenski.booked.repos.BooksRepo
import dev.benkelenski.booked.routes.toApi
import dev.benkelenski.booked.services.BooksService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.config.Environment
import org.http4k.config.EnvironmentKey
import org.http4k.lens.secret
import org.http4k.lens.string
import org.http4k.server.Jetty
import org.http4k.server.asServer

val dbUrl = EnvironmentKey.string().required("DB_URL")
val dbUser = EnvironmentKey.string().optional("DB_USER")
val dbPass = EnvironmentKey.secret().optional("DB_PASS")

val logger = KotlinLogging.logger {}

fun createApp(env: Environment): BooksService {
    val dbConfig = HikariConfig().apply {
        jdbcUrl = env[dbUrl]
        username = env[dbUser]
        password = env[dbPass]?.use { it }
    }

    val database = HikariDataSource(dbConfig)
        .asJdbcDriver()
        .also { Database.Schema.create(it) }
        .let { Database(it) }

    return BooksService(
        booksRepo = BooksRepo(database.booksQueries)
    )
}

fun main() {
    val port = 8080
    logger.info { "creating app" }
    val service = createApp(env = Environment.ENV)
    logger.info { "starting app on port: $port" }
    service
        .toApi()
        .asServer(Jetty(port))
        .start()
}