package org.mpierce.ktordemo

import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.mpierce.ktordemo.jooq.Tables
import java.io.Closeable


class DbTestHelper : Closeable {
    private val dataSource: HikariDataSource = buildDataSource("localhost", 25432, "ktor-demo-test", "ktor-demo-test",
            "ktor-demo-test", 2, 1)

    val dslContext: DSLContext

    init {
        dslContext = DSL.using(dataSource, SQLDialect.POSTGRES)
    }

    fun deleteAllRows() {
        dslContext.transaction { c ->
            c.dsl().apply {
                batch(
                        deleteFrom(Tables.WIDGETS)
                        // other tables as needed
                )
                        .execute()
            }
        }
    }

    override fun close() {
        dslContext.close()
        dataSource.close()
    }
}

class MemoryDaoFactory : DaoFactory {
    // need to re-use the same instance to avoid throwing away data
    private val widgetDao = MemoryWidgetDao()

    override fun widgetDao(txnContext: DSLContext): WidgetDao = widgetDao

}
