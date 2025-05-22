package dev.benkelenski.booked


import java.time.Instant
import java.util.*

data class Book(
    val id: UUID,
    val title: String,
    val author: String,
    val createdAt: Instant,
//    val publisher: String,
//    val isbn: String,
)

data class BookRequest(
    val title: String,
    val author: String,
    val publisher: String?,
    val isbn: String?,
)