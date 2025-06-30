import dev.benkelenski.booked.models.BookTable
import dev.benkelenski.booked.models.ShelfTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object TestUtils {
    fun buildTables() = transaction { SchemaUtils.create(BookTable, ShelfTable) }

    fun dropTables() = transaction { SchemaUtils.drop(BookTable, ShelfTable) }
}
