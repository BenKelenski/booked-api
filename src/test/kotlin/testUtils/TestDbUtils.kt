package testUtils

import dev.benkelenski.booked.models.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertReturning
import org.jetbrains.exposed.sql.transactions.transaction

object TestDbUtils {
    fun buildTables() = transaction {
        SchemaUtils.create(AuthIdentities, Books, RefreshTokens, Shelves, Users)
    }

    fun dropTables() = transaction {
        SchemaUtils.drop(AuthIdentities, Books, RefreshTokens, Shelves, Users)
    }

    fun seedData() = transaction {
        val userId = // User with 3 shelves and 3 books
            Users.insertReturning {
                    it[Users.email] = "test@test.com"
                    it[Users.name] = "testuser"
                }
                .single()[Users.id]

        Users.insert {
            it[Users.email] = "test2@test.com"
            it[Users.name] = "testuser2"
        }

        val shelfId =
            Shelves.insertReturning {
                    it[Shelves.userId] = userId
                    it[Shelves.name] = "To Read"
                }
                .single()[Shelves.id]

        Shelves.insert {
            it[Shelves.userId] = userId
            it[Shelves.name] = "Reading"
        }

        Shelves.insert {
            it[Shelves.userId] = userId
            it[Shelves.name] = "Finished"
        }

        Books.insert {
            it[Books.googleId] = "google1"
            it[Books.title] = "Red Rising"
            it[Books.authors] = listOf("Pierce Brown")
            it[Books.userId] = userId
            it[Books.shelfId] = shelfId
        }

        Books.insert {
            it[Books.googleId] = "google2"
            it[Books.title] = "Golden Son"
            it[Books.authors] = listOf("Pierce Brown")
            it[Books.userId] = userId
            it[Books.shelfId] = shelfId
        }

        Books.insert {
            it[Books.googleId] = "google3"
            it[Books.title] = "Morning Star"
            it[Books.authors] = listOf("Pierce Brown")
            it[Books.userId] = userId
            it[Books.shelfId] = shelfId
        }
    }
}
