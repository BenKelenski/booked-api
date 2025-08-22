package dev.benkelenski.booked.repos

import dev.benkelenski.booked.domain.User
import dev.benkelenski.booked.models.AuthIdentities
import dev.benkelenski.booked.models.Users
import dev.benkelenski.booked.utils.PasswordUtils
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class UserRepo {

    fun getAllUsers(): List<User> = Users.selectAll().map { it.toUser() }

    fun getUserById(id: Int): User? =
        Users.selectAll().where { Users.id eq id }.map { it.toUser() }.singleOrNull()

    fun getOrCreateUser(
        provider: String,
        providerUserId: String,
        email: String?,
        name: String?,
        password: String? = null,
    ): GetOrCreateUserResult {
        val row =
            (AuthIdentities innerJoin Users)
                .selectAll()
                .where {
                    (AuthIdentities.provider eq provider) and
                        (AuthIdentities.providerUserId eq providerUserId)
                }
                .singleOrNull()

        if (row != null) {
            return GetOrCreateUserResult.Existing(row.toUser())
        }

        // Create user
        val newUser =
            Users.insertReturning {
                    it[Users.email] = email
                    it[Users.name] = name
                }
                .map { it.toUser() }
                .single()

        val hash =
            if (provider == "email" && password != null) {
                PasswordUtils.hash(password)
            } else null

        AuthIdentities.insert {
            it[AuthIdentities.userId] = newUser.id
            it[AuthIdentities.provider] = provider
            it[AuthIdentities.providerUserId] = providerUserId
            it[AuthIdentities.email] = email
            it[AuthIdentities.passwordHash] = hash
        }

        return GetOrCreateUserResult.Created(newUser)
    }

    fun findUserByEmailAndPassword(email: String, password: String): User? {
        val row =
            (AuthIdentities innerJoin Users)
                .selectAll()
                .where {
                    (AuthIdentities.provider eq "email") and
                        (AuthIdentities.providerUserId eq email)
                }
                .singleOrNull() ?: return null

        val hash = row[AuthIdentities.passwordHash]
        if (hash != null && PasswordUtils.verify(password, hash)) {
            return row.toUser()
        }

        return null
    }

    fun findUserByProvider(provider: String, providerUserId: String): User? =
        (AuthIdentities innerJoin Users)
            .selectAll()
            .where {
                (AuthIdentities.provider eq provider) and
                    (AuthIdentities.providerUserId eq providerUserId)
            }
            .map { it.toUser() }
            .singleOrNull()

    fun existsById(id: Int): Boolean = Users.selectAll().where { Users.id eq id }.any()

    fun deleteUser(id: Int): Int = Users.deleteWhere { Users.id eq id }
}

fun ResultRow.toUser() =
    User(
        id = this[Users.id],
        email = this[Users.email],
        name = this[Users.name],
        createdAt = this[Users.createdAt].toInstant(),
    )

sealed class GetOrCreateUserResult {
    abstract val user: User

    data class Created(override val user: User) : GetOrCreateUserResult()

    data class Existing(override val user: User) : GetOrCreateUserResult()
}
