package org.mpierce.ktordemo

import org.jooq.DSLContext
import org.mpierce.ktordemo.jooq.Tables.WIDGETS

class SqlWidgetDao(private val txnContext: DSLContext) : WidgetDao {
    override fun getWidget(id: Int): Widget? {
        val r = txnContext.selectFrom(WIDGETS)
                .where(WIDGETS.ID.eq(id))
                .fetchOne()

        return r?.let { Widget(it) }
    }

    override fun getAllWidgets(): List<Widget> {
        // selecting from a table yields typed `WidgetsRecord` objects -- no raw ResultSet wrangling
        return txnContext.selectFrom(WIDGETS)
                .orderBy(WIDGETS.ID.asc())
                .fetch()
                .map { r -> Widget(r) }
                .toList()
    }

    override fun createWidget(name: String): Widget {
        // This method returns a Widget, which includes a `createdAt` timestamp set by the database.
        // In PostgreSQL, we can use the RETURNING clause to insert a new record and get back the db-generated
        // primary key and timestamp as follows, which produces a fully populated WidgetsRecord:
        /*
            val result = txnContext.insertInto(WIDGETS, WIDGETS.NAME)
            .values(name)
            .returning()
            .fetchOne()
         */
        // See https://www.jooq.org/doc/3.11/manual/sql-building/sql-statements/insert-statement/insert-returning/.
        // However, just to show how to do it with other databases that lack support for that, here we'll use a
        // WidgetsRecord to create a new row with a separate INSERT and SELECT.

        val record = txnContext.newRecord(WIDGETS).apply {
            this.name = name
            store()
            // the db row exists, but this object doesn't know about db-generated data, so we explicitly refresh
            refresh()
        }

        return Widget(record)
    }

    override fun updateWidgetName(id: Int, name: String): Widget {
        // Just as a demonstration, we'll use a different style from `createWidget()`.
        // You can also use `WidgetsRecord` for this, though:
        // https://www.jooq.org/doc/3.11/manual/sql-execution/crud-with-updatablerecords/simple-crud/
        val result = txnContext.update(WIDGETS)
                .set(WIDGETS.NAME, name)
                .where(WIDGETS.ID.eq(id))
                .returning()
                .fetchOne()

        return Widget(result)
    }
}

class SqlDaoFactory : DaoFactory {
    override fun widgetDao(txnContext: DSLContext): WidgetDao = SqlWidgetDao(txnContext)
}
