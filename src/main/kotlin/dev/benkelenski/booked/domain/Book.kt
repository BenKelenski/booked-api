package dev.benkelenski.booked.domain

import com.squareup.moshi.Json
import java.time.Instant

data class Book(
    val id: Int,
    @Json(name = "google_id") val googleId: String,
    val title: String,
    val authors: List<String>,
    @Json(name = "thumbnail_url") val thumbnailUrl: String?,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "user_id") val userId: Int,
    @Json(name = "shelf_id") val shelfId: Int,
)
