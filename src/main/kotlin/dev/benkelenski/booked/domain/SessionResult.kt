package dev.benkelenski.booked.domain

data class SessionResult(val user: User, val accessToken: String, val refreshToken: String)
