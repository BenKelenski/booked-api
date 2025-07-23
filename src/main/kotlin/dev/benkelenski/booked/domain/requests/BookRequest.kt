package dev.benkelenski.booked.domain.requests

import com.squareup.moshi.Json

data class BookRequest(@Json(name = "volume_id") val volumeId: String)
