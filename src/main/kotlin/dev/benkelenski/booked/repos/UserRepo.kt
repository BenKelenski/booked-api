package dev.benkelenski.booked.repos

import dev.benkelenski.booked.domain.User
import dev.benkelenski.booked.models.UserTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class UserRepo {

    fun getAllUsers(): List<User> = transaction {
        addLogger(StdOutSqlLogger)
        UserTable.selectAll().map { it.toUser() }
    }

    fun getUserById(id: Int): User? = transaction {
        addLogger(StdOutSqlLogger)
        UserTable.selectAll().where { UserTable.id eq id }.map { it.toUser() }.singleOrNull()
    }

    fun createOrGetUser(id: String): String = id

    fun deleteUser(id: String): Int = 1
}

fun ResultRow.toUser() =
    User(
        id = this[UserTable.id],
        email = this[UserTable.email],
        name = this[UserTable.name],
        createdAt = this[UserTable.createdAt].toInstant(),
    )
