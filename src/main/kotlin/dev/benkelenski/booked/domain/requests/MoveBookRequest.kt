package dev.benkelenski.booked.domain.requests

import com.squareup.moshi.Json

data class MoveBookRequest(@param:Json(name = "shelf_id") val shelfId: Int)
