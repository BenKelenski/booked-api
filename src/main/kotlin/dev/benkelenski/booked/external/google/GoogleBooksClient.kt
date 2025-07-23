package dev.benkelenski.booked.external.google

import dev.benkelenski.booked.external.google.dto.SearchResultDto
import dev.benkelenski.booked.external.google.dto.VolumeDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.*
import org.http4k.format.Moshi.auto
import org.http4k.lens.Query
import org.http4k.lens.string

val queryLens = Query.string().optional("q")
val projectionLens = Query.string().optional("projection")
val apikeyLens = Query.string().required("key")
val searchResultDtoLens = Body.auto<SearchResultDto>().toLens()
val volumeDtoLens = Body.auto<VolumeDto>().toLens()

class GoogleBooksClient(
    private val host: Uri,
    private val apiKey: String,
    private val internet: HttpHandler,
) {

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val PROJECTION = "lite"
    }

    fun search(query: String?): Array<VolumeDto>? {
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

        val searchResultDto: SearchResultDto? = searchResultDtoLens(response)

        return searchResultDto?.items?.toTypedArray()
    }

    fun getVolume(id: String): VolumeDto? {
        val request =
            Request(Method.GET, host.path("/books/v1/volumes/$id")).with(apikeyLens of apiKey)

        val response = internet(request)

        if (!response.status.successful) {
            logger.error { "Error performing search: ${response.bodyString()}" }
            throw RuntimeException("Error performing search: ${response.bodyString()}")
        }

        logger.info { "Received response: ${response.bodyString()}" }

        val volumeDto: VolumeDto? = volumeDtoLens(response)

        return volumeDto
    }
}
