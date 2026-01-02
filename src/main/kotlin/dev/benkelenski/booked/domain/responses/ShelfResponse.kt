package dev.benkelenski.booked.domain.responses

import com.squareup.moshi.Json
import dev.benkelenski.booked.domain.Shelf
import dev.benkelenski.booked.domain.ShelfType

data class ShelfResponse(
    val id: Int,
    val name: String,
    val description: String? = null,
    @param:Json(name = "book_count") val bookCount: Long,
    @param:Json(name = "shelf_type") val shelfType: ShelfType,
    @param:Json(name = "created_at") val createdAt: String,
) {
    companion object {
        fun from(shelf: Shelf, bookCount: Long): ShelfResponse =
            ShelfResponse(
                id = shelf.id,
                name = shelf.name,
                description = shelf.description,
                bookCount = bookCount,
                shelfType = shelf.shelfType,
                createdAt = shelf.createdAt.toString(),
            )
    }
}
