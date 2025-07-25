package dev.benkelenski.booked.services

import dev.benkelenski.booked.external.google.GoogleBooksClient
import dev.benkelenski.booked.external.google.dto.VolumeDto
import io.github.oshai.kotlinlogging.KotlinLogging

/** alias for [GoogleBooksService.searchWithQuery] */
typealias SearchWithQuery = (query: String?) -> Array<VolumeDto>?

/** alias for [GoogleBooksService.fetchByVolumeId] */
typealias FetchByVolumeId = (id: String) -> VolumeDto?

class GoogleBooksService(private val googleBooksClient: GoogleBooksClient) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    fun searchWithQuery(query: String?): Array<VolumeDto>? {
        logger.info { "Searching for $query" }
        return googleBooksClient.search(query)
    }

    fun fetchByVolumeId(id: String): VolumeDto? {
        logger.info { "Fetching book $id" }
        return googleBooksClient.getVolume(id)
    }
}
