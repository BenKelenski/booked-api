package dev.benkelenski.booked.domain.requests

import com.squareup.moshi.Json

data class AddBookRequest(
    @param:Json(name = "shelf_id") val shelfId: Int,
    @param:Json(name = "volume_id") val volumeId: String,
)
