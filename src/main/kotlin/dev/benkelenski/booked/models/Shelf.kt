package dev.benkelenski.booked.models

import java.time.Instant

data class Shelf(
    val id: Int,
    val name: String,
    val description: String?,
    val createdAt: Instant,
)

data class ShelfRequest(
    val name: String,
    val description: String?,
)