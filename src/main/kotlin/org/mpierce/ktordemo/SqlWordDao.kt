package org.mpierce.ktordemo

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.mpierce.ktordemo.jooq.Tables

class SqlWordDao(private val txnContext: DSLContext) : WordDao {
    override fun getWord(id: Int): Word? {
        val r = txnContext.selectFrom(Tables.WORDS)
            .where(Tables.WORDS.ID.eq(id))
            .fetchOne()

        return r?.let { Word(it) }
    }

    override fun getAllWords(): List<Word> {
        // selecting from a table yields typed `WordsRecord` objects -- no raw ResultSet wrangling
        return txnContext.selectFrom(Tables.WORDS)
            .orderBy(Tables.WORDS.ID.asc())
            .fetch()
            .map { r -> Word(r) }
            .toList()
    }

    override fun createWord(word: String): Word {
        // This method returns a Word, which includes a `createdAt` timestamp set by the database.
        // If we wanted to use plain SQL, we can do it with `insertInto()`.
        // In PostgreSQL, we can use the RETURNING clause to insert a new record and get back the db-generated
        // primary key and timestamp as follows, which produces a fully populated WordsRecord:
        /*
            val result = txnContext.insertInto(WORDS, WORDS.NAME)
            .values(name)
            .returning()
            .fetchOne()
         */
        // See https://www.jooq.org/doc/3.11/manual/sql-building/sql-statements/insert-statement/insert-returning/.
        // However, we can also do it using the generated UpdatableRecord implementation for the widgets table:
        val record = txnContext.newRecord(Tables.WORDS).apply {
            this.word = word
            // this inserts the row, and since we're on Postgres, we also have jOOQ configured (see `buildJooqDsl()`)
            // to use INSERT ... RETURNING, which means that this will also populate the id and createdAt values, which
            // the db generates.
            store()
            // If we weren't able to use INSERT ... RETURNING, we'd need to also do a refresh() here.
        }

        return Word(record)
    }

    override fun updateWord(id: Int, word: String): Word {
        // Just as a demonstration, we'll use a different style from `createWord()`.
        // You can also use `WordsRecord` for this, though:
        // https://www.jooq.org/doc/3.11/manual/sql-execution/crud-with-updatablerecords/simple-crud/
        val result = txnContext.update(Tables.WORDS)
            .set(Tables.WORDS.WORD, word)
            .where(Tables.WORDS.ID.eq(id))
            .returning()
            .fetchSingle()

        return Word(result)
    }

    override fun wordFirstLetterCounts(): Map<String, Int> {
        // This isn't the only way to do it in SQL, but this way demonstrates some of the flexibility of jOOQ:
        // we're creating a select expression to use as a table, and referencing a column from within that expression.
        // This shows how to use both code gen'd tables and columns like WIDGET.NAME as well as dynamic things like
        // our "prefixes" table and "prefix" column.

        val prefixes = txnContext
            .select(DSL.upper(DSL.left(Tables.WORDS.WORD, 1)).`as`("prefix"))
            .from(Tables.WORDS)
            .asTable("prefixes")

        val field = prefixes.field("prefix")!!.coerce(SQLDataType.CLOB)

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
