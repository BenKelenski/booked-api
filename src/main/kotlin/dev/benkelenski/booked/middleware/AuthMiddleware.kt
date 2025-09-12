package dev.benkelenski.booked.middleware

import dev.benkelenski.booked.auth.TokenProvider
import dev.benkelenski.booked.constants.ErrorCodes
import dev.benkelenski.booked.constants.ErrorTypes
import dev.benkelenski.booked.domain.responses.ApiError
import dev.benkelenski.booked.http.apiErrorLens
import org.http4k.core.*
import org.http4k.core.cookie.cookie

/** alias for [authMiddleware] */
typealias AuthMiddleware = Filter

fun authMiddleware(tokenProvider: TokenProvider): Filter = Filter { next ->
    { req: Request ->
        val token =
            req.cookie("access_token")?.value
                ?: return@Filter Response(Status.UNAUTHORIZED)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Missing access token",
                                code = ErrorCodes.MISSING_ACCESS_TOKEN,
                                type = ErrorTypes.AUTHENTICATION,
                            )
                    )

        val userId =
            tokenProvider.extractUserId(token)
                ?: return@Filter Response(Status.UNAUTHORIZED)
                    .with(
                        Body.apiErrorLens of
                            ApiError(
                                message = "Invalid token",
                                code = ErrorCodes.INVALID_ACCESS_TOKEN,
                                type = ErrorTypes.AUTHENTICATION,
                            )
                    )

        next(req.header("X-User-Id", userId.toString()))
    }
}
