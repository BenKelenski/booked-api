package dev.benkelenski.booked.http

import dev.benkelenski.booked.domain.responses.ApiError
import org.http4k.core.Body
import org.http4k.format.Moshi.auto

val Body.Companion.apiErrorLens
    get() = Body.auto<ApiError>().toLens()
