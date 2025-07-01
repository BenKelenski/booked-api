package dev.benkelenski.booked.models

import com.squareup.moshi.Json
import java.time.Instant

data class Shelf(
    val id: Int,
    @Json(name = "user_id") val userId: String,
    val name: String,
    val description: String?,
    @Json(name = "created_at") val createdAt: Instant,
)

data class ShelfRequest(val name: String, val description: String?)
