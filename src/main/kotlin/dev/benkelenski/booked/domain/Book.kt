package dev.benkelenski.booked.domain

import java.time.Instant

data class Book(
    val id: Int,
    val googleId: String,
    val title: String,
    val authors: List<String>,
    val thumbnailUrl: String?,
    val createdAt: Instant,
    val userId: Int,
    val shelfId: Int,
)
