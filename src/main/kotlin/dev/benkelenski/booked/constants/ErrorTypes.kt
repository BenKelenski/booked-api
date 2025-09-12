package dev.benkelenski.booked.constants

object ErrorTypes {
    // Client Errors (4xx)
    const val AUTHENTICATION = "authentication_error"
    const val AUTHORIZATION = "authorization_error"
    const val VALIDATION = "validation_error"
    const val NOT_FOUND = "not_found_error"
    const val CONFLICT = "conflict_error"

    // Server Errors (5xx)
    const val SYSTEM = "system_error"
    const val SERVICE = "service_error"
    const val DATABASE = "database_error"
    const val EXTERNAL_SERVICE = "external_service_error"
}
