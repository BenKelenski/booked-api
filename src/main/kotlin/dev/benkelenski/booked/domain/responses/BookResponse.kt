package dev.benkelenski.booked.domain.responses

import com.squareup.moshi.Json
import dev.benkelenski.booked.domain.Book

data class BookResponse(
    val googleId: String,
    val title: String,
    val authors: List<String>,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "created_at") val createdAt: String,
) {
    companion object {
        fun from(book: Book): BookResponse =
            BookResponse(
                googleId = book.googleId,
                title = book.title,
                authors = book.authors,
                thumbnailUrl = book.thumbnailUrl,
                createdAt = book.createdAt.toString(),
            )
    }
}
