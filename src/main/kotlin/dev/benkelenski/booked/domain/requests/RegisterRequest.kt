package dev.benkelenski.booked.domain.requests

import com.squareup.moshi.Json

data class RegisterRequest(
    val email: String,
    val password: String,
    @param:Json(name = "display_name") val displayName: String,
)
