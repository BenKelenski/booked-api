package dev.benkelenski.booked.constants

object ErrorCodes {
    // Authentication & Authorization (401, 403)
    const val MISSING_ACCESS_TOKEN = "MISSING_ACCESS_TOKEN"
    const val INVALID_ACCESS_TOKEN = "INVALID_ACCESS_TOKEN"
    const val EXPIRED_ACCESS_TOKEN = "EXPIRED_ACCESS_TOKEN"
    const val INSUFFICIENT_PERMISSIONS = "INSUFFICIENT_PERMISSIONS"

    // Request Validation (400)
    const val MISSING_SHELF_REQUEST = "MISSING_SHELF_REQUEST"
    const val MISSING_SHELF_ID = "MISSING_SHELF_ID"
    const val BLANK_SHELF_NAME = "BLANK_SHELF_NAME"
    const val MISSING_BOOK_REQUEST = "MISSING_BOOK_REQUEST"
    const val MISSING_BOOK_ID = "MISSING_BOOK_ID"
    const val BLANK_GOOGLE_VOLUME_ID = "BLANK_GOOGLE_VOLUME_ID"

    // Not Found (404)
    const val SHELF_NOT_FOUND = "SHELF_NOT_FOUND"
    const val BOOK_NOT_FOUND = "BOOK_NOT_FOUND"

    // Conflict/Business Rules (409, 422)
    const val SHELF_ALREADY_EXISTS = "SHELF_ALREADY_EXISTS"
    const val BOOK_ALREADY_EXISTS = "BOOK_ALREADY_EXISTS"

    // Server Errors (5XX)
    const val INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR"
}
