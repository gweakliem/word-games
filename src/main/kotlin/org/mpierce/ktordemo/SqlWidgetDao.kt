package org.mpierce.ktordemo

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
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

    override fun widgetNameFirstLetterCounts(): Map<String, Int> {
        // This isn't the only way to do it in SQL, but this way demonstrates some of the flexibility of jOOQ:
        // we're creating a select expression to use as a table, and referencing a column from within that expression.
        // This shows how to use both code gen'd tables and columns like WIDGET.NAME as well as dynamic things like
        // our "prefixes" table and "prefix" column.

        val prefixes = txnContext
                .select(DSL.upper(DSL.left(WIDGETS.NAME, 1)).`as`("prefix"))
                .from(WIDGETS)
                .asTable("prefixes")

        val field = prefixes.field("prefix").coerce(SQLDataType.CLOB)

        return txnContext
                .select(field, DSL.count())
                .from(prefixes)
                .groupBy(field)
                .orderBy(field)
                .fetch()
                // turn the Record2 tuple type into a Kotlin Pair
                .map { Pair(it.value1(), it.value2()) }
                // treat the Pairs as key -> value in a map, which keeps iteration order
                .toMap()
    }
}

class SqlDaoFactory : DaoFactory {
    override fun widgetDao(txnContext: DSLContext): WidgetDao = SqlWidgetDao(txnContext)
}
