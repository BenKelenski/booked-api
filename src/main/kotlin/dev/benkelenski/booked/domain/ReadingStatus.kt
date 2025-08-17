package dev.benkelenski.booked.domain

enum class ReadingStatus {
    TO_READ,
    READING,
    FINISHED;

    companion object {
        fun fromString(raw: String): ReadingStatus =
            ReadingStatus.entries.find { it.name.equals(raw, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Invalid ReadingStatus: $raw. Allowed: ${entries.joinToString { it.name }}"
                )
    }
}
