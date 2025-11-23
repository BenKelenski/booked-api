package dev.benkelenski.booked.domain

import dev.benkelenski.booked.domain.requests.*
import dev.benkelenski.booked.domain.responses.*
import org.http4k.core.Body
import org.http4k.format.Moshi.auto
import org.http4k.lens.Path
import org.http4k.lens.int

// Auth lenses
val Body.Companion.registerReqLens
    get() = Body.auto<RegisterRequest>().toLens()

val Body.Companion.loginReqLens
    get() = Body.auto<LoginRequest>().toLens()

val Body.Companion.oauthReqLens
    get() = Body.auto<OAuthRequest>().toLens()

val Body.Companion.authStatusResLens
    get() = Body.auto<AuthStatusResponse>().toLens()

// User Lenses
val Body.Companion.userResLens
    get() = Body.auto<UserResponse>().toLens()

// Shelf lenses
val shelfIdLens = Path.int().of("shelf_id")

val Body.Companion.shelfReqLens
    get() = Body.auto<ShelfRequest>().toLens()

val Body.Companion.shelfResLens
    get() = Body.auto<ShelfResponse>().toLens()

val Body.Companion.shelvesResLens
    get() = Body.auto<Array<ShelfResponse>>().toLens()

// Book lenses
val bookIdLens = Path.int().of("book_id")

val Body.Companion.bookReqLens
    get() = Body.auto<BookRequest>().toLens()

val Body.Companion.bookResLens
    get() = Body.auto<BookResponse>().toLens()

val Body.Companion.booksResLens
    get() = Body.auto<Array<BookResponse>>().toLens()

val Body.Companion.bookPatchLens
    get() = Body.auto<UpdateBookPatch>().toLens()

val Body.Companion.completeBookLens
    get() = Body.auto<CompleteBookRequest>().toLens()

// Error lenses
val Body.Companion.apiErrorLens
    get() = Body.auto<ApiError>().toLens()
