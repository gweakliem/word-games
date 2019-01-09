package org.mpierce.ktordemo

import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class WidgetDaoTestBase {
    abstract fun daoFactory(): DaoFactory

    abstract fun dslContext(): DSLContext

    @Test
    internal fun getBadIdReturnsNull() {
        dslContext().transaction { t ->
            assertNull(daoFactory().widgetDao(t.dsl()).getWidget(12345))
        }
    }

    @Test
    internal fun getGoodIdReturnsWidget() {
        dslContext().transaction { t ->
            val widgetDao = daoFactory().widgetDao(t.dsl())

            val w = widgetDao.createWidget("foo")

            assertEquals(w, widgetDao.getWidget(w.id))
        }
    }

    @Test
    internal fun getAllIncludesAllCreatedWidgets() {
        dslContext().transaction { t ->
            val widgetDao = daoFactory().widgetDao(t.dsl())

            val w1 = widgetDao.createWidget("foo")
            val w2 = widgetDao.createWidget("bar")

            assertEquals(listOf(w1, w2), widgetDao.getAllWidgets())
        }
    }
}

class MemoryWidgetDaoTest : WidgetDaoTestBase() {
    private val factory = MemoryDaoFactory()
    override fun daoFactory() = factory
    override fun dslContext(): DSLContext = fakeJooqDsl()
}

class SqlWidgetDaoTest : WidgetDaoTestBase() {

    private lateinit var dbHelper: DbTestHelper

    @BeforeEach
    fun setUp() {
        dbHelper = DbTestHelper()
        dbHelper.deleteAllRows()
    }

    @AfterEach
    internal fun tearDown() {
        dbHelper.close()
    }

    override fun daoFactory(): DaoFactory = SqlDaoFactory()

    override fun dslContext(): DSLContext = dbHelper.dslContext
}