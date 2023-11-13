package org.mpierce.ktordemo

import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class WordDaoTestBase {
    abstract fun daoFactory(): DaoFactory

    abstract fun dslContext(): DSLContext

    @Test
    internal fun getBadIdReturnsNull() {
        dslContext().transaction { t ->
            assertNull(daoFactory().wordDao(t.dsl()).getWord(12345))
        }
    }

    @Test
    internal fun getGoodIdReturnsWord() {
        dslContext().transaction { t ->
            val wordDao = daoFactory().wordDao(t.dsl())

            val w = wordDao.createWord("foo")

            assertEquals(w, wordDao.getWord(w.id))
        }
    }

    @Test
    internal fun getAllIncludesAllCreatedWords() {
        dslContext().transaction { t ->
            val wordDao = daoFactory().wordDao(t.dsl())

            val w1 = wordDao.createWord("foo")
            val w2 = wordDao.createWord("bar")

            assertEquals(listOf(w1, w2), wordDao.getAllWords())
        }
    }

    @Test
    internal fun updateChangesWord() {
        dslContext().transaction { t ->
            val wordDao = daoFactory().wordDao(t.dsl())

            val w = wordDao.createWord("foo")
            val newName = "bar"
            wordDao.updateWord(w.id, newName)

            assertEquals(w.copy(word = newName), wordDao.getWord(w.id))
        }
    }

    @Test
    internal fun firstLetterCounts() {
        dslContext().transaction { t ->
            val wordDao = daoFactory().wordDao(t.dsl())

            listOf("kangaroo", "Kookaburra", "KOALA", "platypus", "echidna", "wombat", "wallaby").forEach {
                wordDao.createWord(it)
            }

            val actual = wordDao.wordPrefixCounts()
            assertEquals(
                mapOf(
                    "E" to 1,
                    "K" to 3,
                    "P" to 1,
                    "W" to 2,
                ),
                actual,
            )
            // in alphabetical order
            assertEquals(listOf("E", "K", "P", "W"), actual.keys.toList())
        }
    }
}

class MemoryWordDaoTest : WordDaoTestBase() {
    private val factory = MemoryDaoFactory()
    override fun daoFactory() = factory
    override fun dslContext(): DSLContext = fakeJooqDsl()
}

class SqlWordDaoTest : WordDaoTestBase() {

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
