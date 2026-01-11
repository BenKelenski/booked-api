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
            ShelfType.TO_READ ->
                this.copy(
                    shelfId = targetShelfId,
                    currentPage = null,
                    updatedAt = Instant.now(),
                    finishedAt = null,
                )
            ShelfType.READING ->
                this.copy(
                    shelfId = targetShelfId,
                    currentPage =
                        0, // Rule: Moving to a READING shelfType starts the currentPage at 0
                    updatedAt = Instant.now(),
                    finishedAt = null,
                )
            ShelfType.FINISHED -> this.complete(targetShelfId, this.rating, this.review)
            ShelfType.CUSTOM ->
                this.copy(
                    shelfId = targetShelfId,
                    currentPage =
                        null, // Rule: Moving to any other shelfType resets the currentPage to null
                    finishedAt = null,
                    updatedAt = Instant.now(),
                )
        }

    fun updateProgress(latestPage: Int): Book {
        if (this.pageCount != null && latestPage > this.pageCount) {
            throw IllegalArgumentException(
                "Current page cannot exceed total page count: ${this.pageCount}"
            )
        }

        return this.copy(currentPage = latestPage)
    }

    fun complete(shelfId: Int, rating: Int?, review: String?): Book {
        val now = Instant.now()
        return this.copy(
            shelfId = shelfId,
            currentPage = null,
            rating = rating,
            review = review,
            finishedAt = now,
            updatedAt = now,
        )
    }
}
