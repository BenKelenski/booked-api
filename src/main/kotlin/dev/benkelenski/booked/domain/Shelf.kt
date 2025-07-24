package dev.benkelenski.booked.domain

import com.squareup.moshi.Json
import java.time.Instant

data class Shelf(
    val id: Int,
    @Json(name = "user_id") val userId: Int,
    val name: String,
    val description: String?,
    @Json(name = "created_at") val createdAt: Instant,
)
