package dev.benkelenski.booked.domain

data class AuthPayload(
    val provider: String,
    val providerUserId: String,
    val name: String?,
    val email: String?,
)
