package dev.benkelenski.booked.clients

import dev.benkelenski.booked.domain.DataBook
import dev.benkelenski.booked.domain.GoogleBooksResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import org.http4k.lens.Query
import org.http4k.lens.string

val queryLens = Query.string().optional("q")
val projectionLens = Query.string().optional("projection")
val apikeyLens = Query.string().required("key")
val googleBooksResponseLens = Body.auto<GoogleBooksResponse>().toLens()

class GoogleBooksClient(
    private val host: Uri,
    private val apiKey: String,
    private val internet: HttpHandler,
) {

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val PROJECTION = "lite"
    }

    fun search(query: String?): Array<DataBook>? {
        logger.info { "Searching for $query..." }
        val request =
            Request(Method.GET, host.path("/books/v1/volumes"))
                .with(queryLens of query)
                .with(projectionLens of PROJECTION)
                .with(apikeyLens of apiKey)

        val response = internet(request)

        if (!response.status.successful) {
            logger.error { "Error performing search: ${response.bodyString()}" }
            throw RuntimeException("Error performing search: ${response.bodyString()}")
        }

        val googleBooksResponse: GoogleBooksResponse? = googleBooksResponseLens(response)

        return googleBooksResponse?.items?.toTypedArray()
    }
}
