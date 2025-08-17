package dev.benkelenski.booked.utils

import org.http4k.core.Request

fun Request.parseUserIdHeader(): Int? = header("X-User-Id")?.toIntOrNull()
