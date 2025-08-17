package dev.benkelenski.booked.domain.requests

import dev.benkelenski.booked.domain.ReadingStatus

data class UpdateBookPatch(
    val progressPercent: Int? = null,
    val status: ReadingStatus? = null,
    val newShelfId: Int? = null,
) {
    fun isEmpty(): Boolean = progressPercent == null && status == null && newShelfId == null
}
