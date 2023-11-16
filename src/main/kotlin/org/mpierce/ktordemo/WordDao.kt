package org.mpierce.ktordemo

import com.fasterxml.jackson.annotation.JsonProperty
import org.mpierce.ktordemo.jooq.tables.records.WordsRecord
import java.time.Instant

interface WordDao {
    /**
     * @return the widget with that id, or null if not found
     */
    fun getWord(id: Int): Word?

    /**
     * @return All widgets in creation order (newest last).
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
     * @return a map of one letter strings to how many widgets had a name that started with that string
     */
    fun wordFirstLetterCounts(): Map<String, Int>
}

data class Word(
    @JsonProperty("id") val id: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("createdAt") val createdAt: Instant,
) {
    constructor(r: WordsRecord) : this(r.id, r.word, r.createdAt.toInstant())
}
