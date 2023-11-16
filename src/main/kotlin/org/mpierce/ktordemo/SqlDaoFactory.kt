package org.mpierce.ktordemo

import org.jooq.DSLContext

class SqlDaoFactory : DaoFactory {
    override fun widgetDao(txnContext: DSLContext): WidgetDao = SqlWidgetDao(txnContext)
    override fun wordDao(txnContext: DSLContext): WordDao = SqlWordDao(txnContext)
}
