package dev.benkelenski.booked.domain.requests

import com.squareup.moshi.Json

data class BookRequest(@param:Json(name = "volume_id") val volumeId: String)
