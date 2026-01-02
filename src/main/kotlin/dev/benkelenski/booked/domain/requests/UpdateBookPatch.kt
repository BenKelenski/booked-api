package dev.benkelenski.booked.domain.requests

import com.squareup.moshi.Json
import dev.benkelenski.booked.domain.ShelfType

data class UpdateBookPatch(
    @param:Json(name = "current_page") val currentPage: Int? = null,
    val status: ShelfType? = null,
    @param:Json(name = "shelf_id") val shelfId: Int? = null,
) {
    fun isEmpty(): Boolean = currentPage == null && status == null && shelfId == null
}
