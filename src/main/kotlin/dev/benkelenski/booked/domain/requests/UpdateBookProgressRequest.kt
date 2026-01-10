package dev.benkelenski.booked.domain.requests

import com.squareup.moshi.Json

data class UpdateBookProgressRequest(
    @param:Json(name = "latest_page") val latestPage: Int,
)
