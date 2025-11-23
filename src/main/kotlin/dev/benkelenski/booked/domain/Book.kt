package dev.benkelenski.booked.domain

import java.time.Instant

data class Book(
    val id: Int,
    val googleId: String,
    val title: String,
    val authors: List<String>,
    val thumbnailUrl: String?,
    val currentPage: Int?,
    val pageCount: Int?,
    val rating: Int?,
    val review: String?,
    val createdAt: Instant,
    val updatedAt: Instant?,
    val finishedAt: Instant?,
    val userId: Int,
    val shelfId: Int,
)
