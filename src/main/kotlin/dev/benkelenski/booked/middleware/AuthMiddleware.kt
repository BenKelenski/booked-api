package dev.benkelenski.booked.middleware

import dev.benkelenski.booked.auth.TokenProvider
import org.http4k.core.Filter
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.cookie

/** alias for [authMiddleware] */
typealias AuthMiddleware = Filter

fun authMiddleware(tokenProvider: TokenProvider): Filter = Filter { next ->
    { req: Request ->
        val token =
            req.cookie("access_token")?.value
                ?: return@Filter Response(Status.UNAUTHORIZED).body("Missing access token")

        val userId =
            tokenProvider.extractUserId(token)
                ?: return@Filter Response(Status.UNAUTHORIZED).body("Invalid or expired token")

        next(req.header("X-User-Id", userId.toString()))
    }
}
