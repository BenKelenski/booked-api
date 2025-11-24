package dev.benkelenski.booked.domain.requests

data class CompleteBookRequest(val rating: Int? = null, val review: String? = null) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (rating != null && (rating !in 1..5)) {
            errors.add("Rating must be between 1 and 5")
        }
        if (review != null && (review.isBlank() || review.length > 255)) {
            errors.add("Review must be at most 255 characters")
        }
        return errors
    }
}
