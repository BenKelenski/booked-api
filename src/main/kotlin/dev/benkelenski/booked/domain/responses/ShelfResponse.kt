package dev.benkelenski.booked.domain.responses

import com.squareup.moshi.Json
import dev.benkelenski.booked.domain.Shelf

data class ShelfResponse(
    val id: Int,
    val name: String,
    val description: String? = null,
    @param:Json(name = "created_at") val createdAt: String,
) {
    companion object {
        fun from(shelf: Shelf): ShelfResponse =
            ShelfResponse(
                id = shelf.id,
                name = shelf.name,
                description = shelf.description,
                createdAt = shelf.createdAt.toString(),
            )
    }
}
