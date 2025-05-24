package dev.benkelenski.booked

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.http4k.config.Environment
import org.http4k.config.EnvironmentKey
import org.http4k.lens.secret
import org.http4k.lens.string
import org.http4k.server.Jetty
import org.http4k.server.asServer

val dbUrl = EnvironmentKey.string().required("DB_URL")
val dbUser = EnvironmentKey.string().optional("DB_USER")
val dbPass = EnvironmentKey.secret().optional("DB_PASS")

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
    // TODO: Add logger
    val service = createApp(env = Environment.ENV)

    service
        .toApi()
        .asServer(Jetty(8080))
        .start()
}