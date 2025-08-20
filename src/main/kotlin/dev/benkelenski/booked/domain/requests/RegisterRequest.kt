package dev.benkelenski.booked.domain.requests

data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String? = null,
)
