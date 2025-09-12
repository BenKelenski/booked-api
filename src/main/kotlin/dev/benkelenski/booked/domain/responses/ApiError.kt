package dev.benkelenski.booked.domain.responses

data class ApiError(val message: String, val code: String, val type: String)
