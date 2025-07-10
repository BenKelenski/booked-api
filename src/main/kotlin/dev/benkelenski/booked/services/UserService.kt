package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.AuthPayload
import dev.benkelenski.booked.domain.User
import dev.benkelenski.booked.domain.UserResponse
import dev.benkelenski.booked.repos.UserRepo

/** alias for [UserService.registerWithEmail] */
typealias RegisterWithEmail = (email: String, password: String, name: String?) -> UserResponse

/** alias for [UserService.loginWithEmail] */
typealias LoginWithEmail = (email: String, password: String) -> UserResponse

/** alias for [UserService.authenticateOrRegister] */
typealias AuthenticateOrRegister = (payload: AuthPayload) -> UserResponse

/** alias for [UserService.getUserById] */
typealias GetUser = (bookId: Int) -> UserResponse?

/** alias for [UserService.getAllUsers] */
typealias GetAllUsers = () -> List<UserResponse>

class UserService(private val userRepo: UserRepo) {

    /** Register a new user using email and password */
    fun registerWithEmail(email: String, password: String, name: String?): UserResponse {
        val existing = userRepo.findUserByProvider("email", email)
        if (existing != null) {
            throw IllegalArgumentException("Email already registered")
        }

        val newUser =
            userRepo.getOrCreateUser(
                provider = "email",
                providerUserId = email,
                email = email,
                name = name,
                password = password,
            )

        return UserResponse.from(newUser)
    }

    /** Log in a user using email and password */
    fun loginWithEmail(email: String, password: String): UserResponse {
        val user =
            userRepo.findUserByEmailAndPassword(email, password)
                ?: throw IllegalArgumentException("Invalid email or password")

        return UserResponse.from(user)
    }

    /** Handle OAuth login or registration (Google, Apple, Facebook) */
    fun authenticateOrRegister(payload: AuthPayload): UserResponse {
        return UserResponse.from(
            userRepo.getOrCreateUser(
                provider = payload.provider,
                providerUserId = payload.providerUserId,
                email = payload.email,
                name = payload.name,
            )
        )
    }

    fun getAllUsers(): List<User> = userRepo.getAllUsers()

    fun getUserById(id: Int): User? = userRepo.getUserById(id)
}
