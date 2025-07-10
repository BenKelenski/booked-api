package dev.benkelenski.booked.repos

import dev.benkelenski.booked.domain.User
import dev.benkelenski.booked.models.AuthIdentities
import dev.benkelenski.booked.models.Users
import dev.benkelenski.booked.utils.PasswordUtils
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class UserRepo {

    fun getAllUsers(): List<User> = transaction {
        addLogger(StdOutSqlLogger)
        Users.selectAll().map { it.toUser() }
    }

    fun getUserById(id: Int): User? = transaction {
        addLogger(StdOutSqlLogger)
        Users.selectAll().where { Users.id eq id }.map { it.toUser() }.singleOrNull()
    }

    fun getOrCreateUser(
        provider: String,
        providerUserId: String,
        email: String?,
        name: String?,
        password: String? = null,
    ): User = transaction {
        val row =
            (AuthIdentities innerJoin Users)
                .selectAll()
                .where {
                    (AuthIdentities.provider eq provider) and
                        (AuthIdentities.providerUserId eq providerUserId)
                }
                .singleOrNull()

        if (row != null) {
            return@transaction row.toUser()
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

        return@transaction newUser
    }

    fun findUserByEmailAndPassword(email: String, password: String): User? = transaction {
        val row =
            (AuthIdentities innerJoin Users)
                .selectAll()
                .where {
                    (AuthIdentities.provider eq "email") and
                        (AuthIdentities.providerUserId eq email)
                }
                .singleOrNull() ?: return@transaction null

        val hash = row[AuthIdentities.passwordHash]
        if (hash != null && PasswordUtils.verify(password, hash)) {
            return@transaction row.toUser()
        }

        return@transaction null
    }

    fun findUserByProvider(provider: String, providerUserId: String): User? = transaction {
        (AuthIdentities innerJoin Users)
            .selectAll()
            .where {
                (AuthIdentities.provider eq provider) and
                    (AuthIdentities.providerUserId eq providerUserId)
            }
            .map { it.toUser() }
            .singleOrNull()
    }

    fun deleteUser(id: Int): Int = transaction {
        addLogger(StdOutSqlLogger)
        Users.deleteWhere { Users.id eq id }
    }
}

fun ResultRow.toUser() =
    User(
        id = this[Users.id],
        email = this[Users.email],
        name = this[Users.name],
        createdAt = this[Users.createdAt].toInstant(),
    )
