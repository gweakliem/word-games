package org.mpierce.ktordemo

import com.google.inject.AbstractModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/**
 * Helper function for the common case of using a dao in a transaction
 */
suspend fun <D, T> DSLContext.txnWithDao(daoBuilder: (DSLContext) -> D, block: (D) -> T): T {
    return withContext(Dispatchers.IO) {
        this@txnWithDao.transactionResult { config ->
            block(daoBuilder(config.dsl()))
        }
    }
}
