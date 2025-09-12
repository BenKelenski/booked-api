package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.responses.UserResponse
import dev.benkelenski.booked.repos.UserRepo
import org.jetbrains.exposed.sql.transactions.transaction

/** alias for [UserService.getAllUsers] */
typealias GetAllUsers = () -> List<UserResponse>

/** alias for [UserService.getUserById] */
typealias GetUserById = (id: Int) -> UserResponse?

class UserService(private val userRepo: UserRepo) {

    fun getAllUsers(): List<UserResponse> = transaction { userRepo.getAllUsers().map { UserResponse.from(user = it) } }

    fun getUserById(id: Int): UserResponse? = transaction { userRepo.getUserById(id)?.let { UserResponse.from(it) } }
}
