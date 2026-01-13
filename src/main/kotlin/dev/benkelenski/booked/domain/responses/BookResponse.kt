package dev.benkelenski.booked.domain.responses

import com.squareup.moshi.Json
import dev.benkelenski.booked.domain.Book

data class BookResponse(
    val id: Int,
    @param:Json(name = "shelf_id") val shelfId: Int,
    @param:Json(name = "google_id") val googleId: String,
    val title: String,
    val authors: List<String>,
    @param:Json(name = "current_page") val currentPage: Int? = null,
    @param:Json(name = "page_count") val pageCount: Int? = null,
    @param:Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @param:Json(name = "created_at") val createdAt: String,
    @param:Json(name = "updated_at") val updatedAt: String? = null,
    @param:Json(name = "finished_at") val finishedAt: String? = null,
)

fun Book.toResponse() =
    BookResponse(
        id = this.id,
        shelfId = this.shelfId,
        googleId = this.googleId,
        title = this.title,
        authors = this.authors,
        currentPage = this.currentPage,
        pageCount = this.pageCount,
        thumbnailUrl = this.thumbnailUrl,
        createdAt = this.createdAt.toString(),
        updatedAt = this.updatedAt?.toString(),
        finishedAt = this.finishedAt?.toString(),
    )
