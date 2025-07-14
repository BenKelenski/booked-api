package dev.benkelenski.booked.services

import dev.benkelenski.booked.domain.User
import dev.benkelenski.booked.repos.UserRepo

class UserService(private val userRepo: UserRepo) {

    fun getAllUsers(): List<User> = userRepo.getAllUsers()

    fun getUserById(id: Int): User? = userRepo.getUserById(id)
}
