package dev.benkelenski.booked.domain.requests

import com.squareup.moshi.Json
import dev.benkelenski.booked.domain.ReadingStatus

data class UpdateBookPatch(
    @param:Json(name = "current_page") val currentPage: Int? = null,
    val status: ReadingStatus? = null,
    @param:Json(name = "new_shelf_id") val newShelfId: Int? = null,
) {
    fun isEmpty(): Boolean = currentPage == null && status == null && newShelfId == null
}
