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
) {
    fun moveToShelf(targetShelfId: Int, targetShelfType: ShelfType): Book =
        when (targetShelfType) {
            ShelfType.READING ->
                this.copy(
                    shelfId = targetShelfId,
                    currentPage =
                        0, // Rule: Moving to a READING shelfType starts the currentPage at 0
                )
            else ->
                this.copy(
                    shelfId = targetShelfId,
                    currentPage =
                        null, // Rule: Moving to any other shelfType resets the currentPage to null
                )
        }

    fun updateProgress(latestPage: Int): Book = this.copy(currentPage = latestPage)
}
