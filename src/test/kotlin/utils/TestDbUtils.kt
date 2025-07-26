package utils

import dev.benkelenski.booked.models.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object TestDbUtils {
    fun buildTables() = transaction {
        SchemaUtils.create(AuthIdentities, Books, RefreshTokens, Shelves, Users)
    }

    fun dropTables() = transaction {
        SchemaUtils.drop(AuthIdentities, Books, RefreshTokens, Shelves, Users)
    }
}
