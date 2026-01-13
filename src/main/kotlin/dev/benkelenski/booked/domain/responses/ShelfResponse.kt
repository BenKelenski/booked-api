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
)

fun Shelf.toResponse() =
    ShelfResponse(
        id = this.id,
        name = this.name,
        description = this.description,
        bookCount = this.bookCount,
        shelfType = this.shelfType,
        createdAt = this.createdAt.toString(),
    )
