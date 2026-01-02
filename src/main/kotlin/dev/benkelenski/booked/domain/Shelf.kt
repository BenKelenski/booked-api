package dev.benkelenski.booked.domain

import java.time.Instant

data class Shelf(
    val id: Int,
    val userId: Int,
    val name: String,
    val description: String?,
    val shelfType: ShelfType,
    val createdAt: Instant,
)

enum class ShelfType {
    TO_READ,
    READING,
    FINISHED,
    CUSTOM;

    companion object {
        fun fromString(raw: String): ShelfType =
            ShelfType.entries.find { it.name.equals(raw, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Invalid ShelfType: $raw. Allowed: ${entries.joinToString { it.name }}"
                )
    }
}
