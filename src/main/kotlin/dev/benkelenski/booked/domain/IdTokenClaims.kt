package dev.benkelenski.booked.domain

data class IdTokenClaims(val subject: String, val name: String?, val email: String?)
