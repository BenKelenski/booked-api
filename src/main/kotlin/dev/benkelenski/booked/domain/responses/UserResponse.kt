package dev.benkelenski.booked.domain.responses

import dev.benkelenski.booked.domain.User

data class UserResponse(val id: Int, val email: String?, val name: String?) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(user.id, user.email, user.name)
    }
}
