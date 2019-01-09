package org.mpierce.ktordemo

import com.google.inject.AbstractModule
import org.jooq.DSLContext

/**
 * This abstraction allows easily plugging in different implementations of persistence while also allowing clear
 * transaction boundaries.
 */
interface DaoFactory {
    fun widgetDao(txnContext: DSLContext): WidgetDao
}

class DaoFactoryModule(private val daoFactory: DaoFactory) : AbstractModule() {
    override fun configure() {
        bind(DaoFactory::class.java).toInstance(daoFactory)
    }
}
