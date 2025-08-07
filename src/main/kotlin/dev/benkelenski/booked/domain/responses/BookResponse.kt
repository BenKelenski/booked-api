package dev.benkelenski.booked.domain.responses

import com.squareup.moshi.Json
import dev.benkelenski.booked.domain.Book

data class BookResponse(
    val id: Int,
    @param:Json(name = "google_id") val googleId: String,
    val title: String,
    val authors: List<String>,
    @param:Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @param:Json(name = "created_at") val createdAt: String,
) {
    companion object {
        fun from(book: Book): BookResponse =
            BookResponse(
                id = book.id,
                googleId = book.googleId,
                title = book.title,
                authors = book.authors,
                thumbnailUrl = book.thumbnailUrl,
                createdAt = book.createdAt.toString(),
            )
    }
}
