package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.User
import dev.benkelenski.booked.repos.UserRepo
import org.jetbrains.exposed.sql.transactions.transaction

class UserService(private val userRepo: UserRepo) {

    fun getAllUsers(): List<User> = transaction { userRepo.getAllUsers() }

    fun getUserById(id: Int): User? = transaction { userRepo.getUserById(id) }
}
