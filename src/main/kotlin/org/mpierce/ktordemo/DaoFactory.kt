package org.mpierce.ktordemo

import org.jooq.DSLContext

/**
 * This abstraction allows easily plugging in different implementations of persistence while also allowing clear
 * transaction boundaries.
 */
interface DaoFactory {
    fun widgetDao(txnContext: DSLContext): WidgetDao
}

class SqlDaoFactory: DaoFactory {
    override fun widgetDao(txnContext: DSLContext): WidgetDao = SqlWidgetDao(txnContext)
}
