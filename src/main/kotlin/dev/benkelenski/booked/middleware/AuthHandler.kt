package dev.benkelenski.booked.middleware

import dev.benkelenski.booked.constants.ErrorCodes
import dev.benkelenski.booked.constants.ErrorTypes
import dev.benkelenski.booked.constants.HttpConstants
import dev.benkelenski.booked.domain.responses.ApiError
import dev.benkelenski.booked.http.apiErrorLens
import org.http4k.core.*

fun authHandler(handler: (userId: Int, request: Request) -> Response): (Request) -> Response =
    { request ->
        val userId = request.header(HttpConstants.USER_ID_HEADER)?.toIntOrNull()
        if (userId != null) {
            handler(userId, request)
        } else {
            // This should never happen if middleware is working correctly
            Response(Status.INTERNAL_SERVER_ERROR)
                .with(
                    Body.apiErrorLens of
                        ApiError(
                            message = "Authentication middleware failed",
                            code = ErrorCodes.INTERNAL_SERVER_ERROR,
                            type = ErrorTypes.SYSTEM,
                        )
                )
        }
    }
