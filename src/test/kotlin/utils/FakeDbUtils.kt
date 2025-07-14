package utils

import dev.benkelenski.booked.models.Books
import dev.benkelenski.booked.models.Shelves
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object FakeDbUtils {
    fun buildTables() = transaction { SchemaUtils.create(Books, Shelves) }

    fun dropTables() = transaction { SchemaUtils.drop(Books, Shelves) }
}
