package dev.benkelenski.booked

import org.http4k.server.Jetty
import org.http4k.server.asServer
import java.security.SecureRandom
import java.time.Clock
import kotlin.random.asKotlinRandom

fun main() {
    val api = BookService(
        clock = Clock.systemUTC(),
        random = SecureRandom().asKotlinRandom()
    ).toApi()

    api.asServer(Jetty(8000)).start()

}