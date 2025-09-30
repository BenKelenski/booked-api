package dev.benkelenski.booked.domain.responses

import com.squareup.moshi.Json
import dev.benkelenski.booked.domain.ReadingStatus
import dev.benkelenski.booked.domain.Shelf

data class ShelfResponse(
    val id: Int,
    val name: String,
    val description: String? = null,
    @param:Json(name = "book_count") val bookCount: Long,
    @param:Json(name = "is_deletable") val isDeletable: Boolean = false,
    @param:Json(name = "reading_status") val readingStatus: ReadingStatus? = null,
    @param:Json(name = "created_at") val createdAt: String,
) {
    companion object {
        fun from(shelf: Shelf, bookCount: Long): ShelfResponse =
            ShelfResponse(
                id = shelf.id,
                name = shelf.name,
                description = shelf.description,
                bookCount = bookCount,
                isDeletable = shelf.isDeletable,
                readingStatus = shelf.readingStatus,
                createdAt = shelf.createdAt.toString(),
            )
    }
}
