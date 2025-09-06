package dev.benkelenski.booked.domain

import dev.benkelenski.booked.domain.requests.RegisterRequest

/**
 * Domain-level validation for user registration. Keep this pure (no DB access). Do uniqueness
 * checks separately in the service layer.
 */
object AuthRules {
    /** A flat, structured validation error you can serialize directly. */
    data class ValidationError(val field: String, val code: String, val message: String)

    data class ValidationResult(val isValid: Boolean, val errors: List<ValidationError>)

    // ---- Tunables ----
    private const val DISPLAY_NAME_MIN = 2
    private const val DISPLAY_NAME_MAX = 50
    private const val PASSWORD_MIN = 12
    private const val PASSWORD_MAX = 128
    private val RESERVED_DISPLAY_NAMES =
        setOf("admin", "administrator", "root", "support", "moderator", "system")
    private val COMMON_PASSWORDS =
        setOf("password", "password1", "password123", "qwerty", "letmein", "123456", "123456789")

    private val EMAIL_REGEX =
        Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\$", RegexOption.IGNORE_CASE)

    private val PASSWORD_LOWER = Regex("[a-z]")
    private val PASSWORD_UPPER = Regex("[A-Z]")
    private val PASSWORD_DIGIT = Regex("\\d")
    private val PASSWORD_SYMBOL = Regex("[^A-Za-z0-9]")
    private val WHITESPACE = Regex("\\s")

    fun validate(req: RegisterRequest): ValidationResult {
        val errors = buildList {
            validateEmail(req.email)?.let { add(it) }
            addAll(validatePassword(req.password))
            validateDisplayName(req.name)?.let { add(it) }
        }
        return ValidationResult(errors.isEmpty(), errors)
    }

    fun validateEmail(email: String): ValidationError? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return error("email", "required", "Email is required.")
        if (trimmed.length > 254)
            return error("email", "max_length", "Email must be at most 254 characters.")
        if (!EMAIL_REGEX.matches(trimmed)) return error("email", "format", "Email is not valid.")
        val local = trimmed.substringBefore("@")
        if (local.startsWith(".") || local.endsWith(".") || ".." in local) {
            return error("email", "format_local", "Email local-part has invalid dots.")
        }
        return null
    }

    fun validatePassword(password: String): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val pwd = password

        if (pwd.isEmpty()) {
            errors += error("password", "required", "Password is required.")
            return errors
        }
        if (pwd.length < PASSWORD_MIN) {
            errors +=
                error(
                    "password",
                    "min_length",
                    "Password must be at least $PASSWORD_MIN characters.",
                )
        }
        if (pwd.length > PASSWORD_MAX) {
            errors +=
                error(
                    "password",
                    "max_length",
                    "Password must be at most $PASSWORD_MAX characters.",
                )
        }
        if (WHITESPACE.containsMatchIn(pwd)) {
            errors += error("password", "whitespace", "Password must not contain whitespace.")
        }
        if (!PASSWORD_LOWER.containsMatchIn(pwd)) {
            errors += error("password", "lowercase", "Password must include a lowercase letter.")
        }
        if (!PASSWORD_UPPER.containsMatchIn(pwd)) {
            errors += error("password", "uppercase", "Password must include an uppercase letter.")
        }
        if (!PASSWORD_DIGIT.containsMatchIn(pwd)) {
            errors += error("password", "digit", "Password must include a number.")
        }
        if (!PASSWORD_SYMBOL.containsMatchIn(pwd)) {
            errors += error("password", "symbol", "Password must include a symbol.")
        }
        val normalized = pwd.lowercase()
        if (normalized in COMMON_PASSWORDS) {
            errors +=
                error(
                    "password",
                    "common",
                    "Password is too common. Please choose something stronger.",
                )
        }
        return errors
    }

    fun validateDisplayName(displayName: String): ValidationError? {
        val name = displayName.trim()
        if (name.isEmpty()) return null
        if (name.length < DISPLAY_NAME_MIN) {
            return error(
                "displayName",
                "min_length",
                "Display name must be at least $DISPLAY_NAME_MIN characters.",
            )
        }
        if (name.length > DISPLAY_NAME_MAX) {
            return error(
                "displayName",
                "max_length",
                "Display name must be at most $DISPLAY_NAME_MAX characters.",
            )
        }
        // Allow letters, numbers, spaces, dashes, underscores, apostrophes, and dots
        if (!name.matches(Regex("^[A-Za-z0-9 _.'-]+$"))) {
            return error("displayName", "charset", "Display name contains invalid characters.")
        }
        if (name.lowercase() in RESERVED_DISPLAY_NAMES) {
            return error("displayName", "reserved", "That display name is not allowed.")
        }
        return null
    }

    fun canonicalizeEmail(email: String): String = email.trim().lowercase()

    private fun error(field: String, code: String, message: String) =
        ValidationError(field = field, code = code, message = message)
}
