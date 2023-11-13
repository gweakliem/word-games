package org.mpierce.ktordemo

import com.fasterxml.jackson.annotation.JsonProperty
import org.mpierce.ktordemo.jooq.tables.records.WordsRecord
import java.time.Instant

/**
 * The way I've chosen to do it in this project is to have 2 implementations of the DAO: one backed by Postgres via
 * Jooq, and another for use in tests that don't care about a SQL database that is a naive in-memory implementation.
 * This allows tests that aren't testing SQL specifically to run much faster.
 */
interface WordDao {
    /**
     * @return the word with that id, or null if not found
     */
    fun getWord(id: Int): Word?

    /**
     * @return All words in creation order (newest last).
     */
    fun getAllWords(): List<Word>

    /**
     * @return the newly created Word
     */
    fun createWord(word: String): Word

    /**
     * @return the updated Word
     */
    fun updateWord(id: Int, word: String): Word

    /**
     * An example of a reporting-type query.
     *
     * @return a map of one letter strings to how many words had a name that started with that string
     */
    fun wordPrefixCounts(): Map<String, Int>
}

/**
 * Since this is such a simple model, we'll let it be serialized directly.
 */
data class Word(
    @JsonProperty("id") val id: Int,
    @JsonProperty("name") val word: String,
    @JsonProperty("createdAt") val createdAt: Instant,
) {
    constructor(r: WordsRecord) : this(r.id, r.name, r.createdAt.toInstant())
}
