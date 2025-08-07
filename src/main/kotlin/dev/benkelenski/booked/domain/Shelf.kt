package dev.benkelenski.booked.domain

import java.time.Instant

data class Shelf(
    val id: Int,
    val userId: Int,
    val name: String,
    val description: String?,
    val createdAt: Instant,
)
