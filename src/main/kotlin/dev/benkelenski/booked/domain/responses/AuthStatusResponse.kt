package dev.benkelenski.booked.domain.responses

import com.squareup.moshi.Json
import dev.benkelenski.booked.domain.User

data class AuthStatusResponse(
    @param:Json(name = "is_authenticated") val isAuthenticated: Boolean,
    val user: User? = null,
)
