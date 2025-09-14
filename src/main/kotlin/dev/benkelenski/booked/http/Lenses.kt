package dev.benkelenski.booked.http

import dev.benkelenski.booked.domain.requests.BookRequest
import dev.benkelenski.booked.domain.requests.ShelfRequest
import dev.benkelenski.booked.domain.responses.ApiError
import dev.benkelenski.booked.domain.responses.ShelfResponse
import org.http4k.core.Body
import org.http4k.format.Moshi.auto
import org.http4k.lens.Path
import org.http4k.lens.int

// Shelf lenses
val shelfIdLens = Path.int().of("shelf_id")

val Body.Companion.shelfReqLens
    get() = Body.auto<ShelfRequest>().toLens()

val Body.Companion.shelfResLens
    get() = Body.auto<ShelfResponse>().toLens()

val Body.Companion.shelvesResLens
    get() = Body.auto<Array<ShelfResponse>>().toLens()

// Book lenses
val Body.Companion.bookReqLens
    get() = Body.auto<BookRequest>().toLens()

// Error lenses
val Body.Companion.apiErrorLens
    get() = Body.auto<ApiError>().toLens()
