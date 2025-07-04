package dev.benkelenski.booked.domain

import com.squareup.moshi.Json
import java.time.Instant

data class Book(
    val id: Int,
    @Json(name = "user_id") val userId: String,
    val title: String,
    val author: String,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "shelf_id") val shelfId: Int,
    //    val publisher: String,
    //    val isbn: String,
)

data class BookRequest(
    val title: String,
    val author: String,
    @Json(name = "shelf_id") val shelfId: Int,
    //    val publisher: String?,
    //    val isbn: String?,
)
