package dev.benkelenski.booked.domain

import java.time.Instant

data class Shelf(
    val id: Int,
    val userId: Int,
    val name: String,
    val description: String?,
    val shelfType: ShelfType,
    val bookCount: Long,
    val createdAt: Instant,
)

enum class ShelfType {
    TO_READ,
    READING,
    FINISHED,
    CUSTOM,
}
