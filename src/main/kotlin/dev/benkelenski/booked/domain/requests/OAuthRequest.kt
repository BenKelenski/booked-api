package dev.benkelenski.booked.domain.requests

data class OAuthRequest(val provider: String, val token: String)
