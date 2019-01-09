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
        return txnContext.selectFrom(WIDGETS)
                .orderBy(WIDGETS.ID.asc())
                .fetch()
                .map { r -> Widget(r) }
                .toList()
    }

    override fun createWidget(name: String): Widget {
        val result = txnContext.insertInto(WIDGETS, WIDGETS.NAME)
                .values(name)
                .returning()
                .fetchOne()

        return Widget(result)
    }
}

class SqlDaoFactory : DaoFactory {
    override fun widgetDao(txnContext: DSLContext): WidgetDao = SqlWidgetDao(txnContext)
}
