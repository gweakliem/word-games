package org.mpierce.ktordemo

import com.fasterxml.jackson.databind.node.ArrayNode
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

internal class WordEndpointsTest {
    /**
     * A contrived test that shows how to *not* use our custom test helper.
     */
    @Test
    internal fun getBadWordIdGets404() {
        withTestApplication({ testAppSetup() }) {
            with(handleRequest(HttpMethod.Get, "/words/id/${Integer.MAX_VALUE}")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    /**
     * Another contrived test, this time showing how to use the injector.
     */
    @Test
    internal fun getWordReturnsWord() {
        withInMemoryTestApp { injector ->
            val jooq = injector.getInstance(DSLContext::class.java)
            val daoFactory = injector.getInstance(DaoFactory::class.java)

            // transaction isn't needed for in-memory dao impl, but you could run this test with SQL and it would work!
            val word = jooq.transactionResult { c ->
                daoFactory.wordDao(c.dsl()).createWord("foo")
            }

            with(handleRequest(HttpMethod.Get, "/words/id/${word.id}")) {
                assertEquals(HttpStatusCode.OK, response.status())

                // compare the json in a way that ignores whitespace, etc
                DEFAULT_JSON_TEST_HELPER.assertJsonStrEquals(
                    """{
                        "id": ${word.id},
                        "name": "${word.word}",
                        "createdAt": "${word.createdAt}"
                    }""",
                    response.content!!,
                )
            }
        }
    }

    @Test
    internal fun createWordEndpointCreatesWord() {
        withInMemoryTestApp { injector ->
            val jooq = injector.getInstance(DSLContext::class.java)
            val daoFactory = injector.getInstance(DaoFactory::class.java)

            with(
                handleRequest(HttpMethod.Post, "/words") {
                    setBody(
                        """
                        {
                            "name": "qwerty"
                        }""",
                    )
                    addHeader("Content-Type", "application/json")
                },
            ) {
                assertEquals(HttpStatusCode.OK, response.status())

                val rootNode = DEFAULT_JSON_TEST_HELPER.reader.readTree(response.content)
                val id = rootNode.at("/id").intValue()

                val word = jooq.transactionResult { c ->
                    daoFactory.wordDao(c.dsl()).getWord(id)
                }

                assertNotNull(word)
                assertEquals("qwerty", word.word)
            }
        }
    }

    @Test
    internal fun getAllWordsGetsThemAll() {
        withInMemoryTestApp { injector ->
            val jooq = injector.getInstance(DSLContext::class.java)
            val daoFactory = injector.getInstance(DaoFactory::class.java)

            val words = jooq.transactionResult { c ->
                listOf(
                    daoFactory.wordDao(c.dsl()).createWord("foo"),
                    daoFactory.wordDao(c.dsl()).createWord("bar"),
                    daoFactory.wordDao(c.dsl()).createWord("baz"),
                )
            }

            with(handleRequest(HttpMethod.Get, "/words/all")) {
                assertEquals(HttpStatusCode.OK, response.status())

                val rootNode = DEFAULT_JSON_TEST_HELPER.reader.readTree(response.content) as ArrayNode

                assertEquals(words.size, rootNode.size())

                words.zip(rootNode).forEach { (word, node) ->
                    assertEquals(word.id, node.at("/id").intValue())
                    assertEquals(word.word, node.at("/name").textValue())
                }
            }
        }
    }
}
