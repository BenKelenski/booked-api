package dev.benkelenski.booked.domain

import java.time.Instant

data class User(val id: Int, val email: String?, val name: String?, val createdAt: Instant)
