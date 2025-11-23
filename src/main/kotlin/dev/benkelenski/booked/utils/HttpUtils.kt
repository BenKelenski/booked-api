package dev.benkelenski.booked.utils

import dev.benkelenski.booked.constants.ErrorTypes
import dev.benkelenski.booked.domain.apiErrorLens
import dev.benkelenski.booked.domain.responses.ApiError
import io.github.oshai.kotlinlogging.KotlinLogging
import org.http4k.core.*
import org.http4k.lens.LensFailure

private val logger = KotlinLogging.logger {}

/**
 * Safely extracts a value using a lens, returning null if extraction fails. Logs the error with the
 * provided message.
 */
fun <T> extractLensOrNull(request: Request, lens: (Request) -> T, errorMessage: String): T? {
    return try {
        lens(request)
    } catch (e: LensFailure) {
        logger.error(e) { errorMessage }
        null
    }
}

/** Creates a standardized validation error response. */
fun createValidationErrorResponse(message: String, code: String): Response {
    return Response(Status.BAD_REQUEST)
        .with(
            Body.apiErrorLens of
                ApiError(
                    message = message,
                    code = code,
                    type = ErrorTypes.VALIDATION,
                )
        )
}
