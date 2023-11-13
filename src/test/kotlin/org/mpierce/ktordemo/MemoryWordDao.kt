package org.mpierce.ktordemo

import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * An in-memory implementation of WordDao to use when testing other logic that needn't use an actual database.
 */
class MemoryWordDao : WordDao {
    private val words = mutableMapOf<Int, Word>()
    private val nextId = AtomicInteger()

    @Synchronized
    override fun getWord(id: Int): Word? = words[id]

    @Synchronized
    override fun getAllWords(): List<Word> = words.values.toList()

    @Synchronized
    override fun createWord(name: String): Word {
        val w = Word(nextId.getAndIncrement(), name, Instant.now())
        words[w.id] = w
        return w
    }

    @Synchronized
    override fun updateWord(id: Int, word: String): Word {
        val word = words[id]!!.copy(word = word)
        words[id] = word
        return word
    }

    @Synchronized
    override fun wordPrefixCounts(): Map<String, Int> {
        val prefix = { s: String -> s.substring(0, 1).uppercase(Locale.US) }
        return words
            .entries
            .sortedBy { it.value.word.let(prefix) }
            .groupBy { it.value.word.let(prefix) }
            .mapValues { (_, values) -> values.size }
    }
}
