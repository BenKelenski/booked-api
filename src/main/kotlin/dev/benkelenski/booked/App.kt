package dev.benkelenski.booked

import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main() {
    val dbConfig = HikariConfig().apply {
        jdbcUrl = System.getenv("DB_URL")!!
        username = System.getenv("DB_USER")!!
        password = System.getenv("DB_PASS")!!
    }

    val database = HikariDataSource(dbConfig)
        .asJdbcDriver()
        .also { Database.Schema.create(it) }
        .let { Database(it) }

    val api = BooksService(
        booksRepo = BooksRepo(database.booksQueries)
    ).toApi()

    api.asServer(Jetty(8080)).start()

}