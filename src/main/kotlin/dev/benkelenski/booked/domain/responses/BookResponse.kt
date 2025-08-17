package dev.benkelenski.booked.domain.responses

import com.squareup.moshi.Json
import dev.benkelenski.booked.domain.Book

data class BookResponse(
    val id: Int,
    @param:Json(name = "shelf_id") val shelfId: Int,
    @param:Json(name = "google_id") val googleId: String,
    val title: String,
    val authors: List<String>,
    val status: String,
    val progressPercent: Int? = null,
    @param:Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @param:Json(name = "created_at") val createdAt: String,
    @param:Json(name = "updated_at") val updatedAt: String? = null,
    @param:Json(name = "finished_at") val finishedAt: String? = null,
) {
    companion object {
        fun from(book: Book): BookResponse =
            BookResponse(
                id = book.id,
                shelfId = book.shelfId,
                googleId = book.googleId,
                title = book.title,
                authors = book.authors,
                status = book.status.name,
                progressPercent = book.progressPercent,
                thumbnailUrl = book.thumbnailUrl,
                createdAt = book.createdAt.toString(),
                updatedAt = book.updatedAt?.toString(),
                finishedAt = book.finishedAt?.toString(),
            )
    }
}
