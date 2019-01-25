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

internal class WidgetEndpointsTest {
    /**
     * A contrived test that shows how to *not* use our custom test helper.
     */
    @Test
    internal fun getBadWidgetIdGets404() {
        withTestApplication({ testAppSetup() }) {
            with(handleRequest(HttpMethod.Get, "/widgets/id/${Integer.MAX_VALUE}")) {
                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        }
    }

    /**
     * Another contrived test, this time showing how to use the injector.
     */
    @Test
    internal fun getWidgetReturnsWidget() {
        withInMemoryTestApp { injector ->
            val jooq = injector.getInstance(DSLContext::class.java)
            val daoFactory = injector.getInstance(DaoFactory::class.java)

            // transaction isn't needed for in-memory dao impl, but you could run this test with SQL and it would work!
            val widget = jooq.transactionResult { c ->
                daoFactory.widgetDao(c.dsl()).createWidget("foo")
            }

            with(handleRequest(HttpMethod.Get, "/widgets/id/${widget.id}")) {
                assertEquals(HttpStatusCode.OK, response.status())

                // compare the json in a way that ignores whitespace, etc
                DEFAULT_JSON_TEST_HELPER.assertJsonStrEquals("""{
                        "id": ${widget.id},
                        "name": "${widget.name}",
                        "createdAt": "${widget.createdAt}"
                    }""", response.content!!)
            }
        }
    }

    @Test
    internal fun createWidgetEndpointCreatesWidget() {
        withInMemoryTestApp { injector ->
            val jooq = injector.getInstance(DSLContext::class.java)
            val daoFactory = injector.getInstance(DaoFactory::class.java)

            with(handleRequest(HttpMethod.Post, "/widgets") {
                setBody("""{
                    "name": "qwerty"
                    }""")
                addHeader("Content-Type", "application/json")
            }) {
                assertEquals(HttpStatusCode.OK, response.status())

                val rootNode = DEFAULT_JSON_TEST_HELPER.reader.readTree(response.content)
                val id = rootNode.at("/id").intValue()

                val widget = jooq.transactionResult { c ->
                    daoFactory.widgetDao(c.dsl()).getWidget(id)
                }

                assertNotNull(widget)
                assertEquals("qwerty", widget.name)
            }
        }
    }

    @Test
    internal fun getAllWidgetsGetsThemAll() {
        withInMemoryTestApp { injector ->
            val jooq = injector.getInstance(DSLContext::class.java)
            val daoFactory = injector.getInstance(DaoFactory::class.java)

            val widgets = jooq.transactionResult { c ->
                listOf(
                        daoFactory.widgetDao(c.dsl()).createWidget("foo"),
                        daoFactory.widgetDao(c.dsl()).createWidget("bar"),
                        daoFactory.widgetDao(c.dsl()).createWidget("baz")
                )
            }

            with(handleRequest(HttpMethod.Get, "/widgets/all")) {
                assertEquals(HttpStatusCode.OK, response.status())

                val rootNode = DEFAULT_JSON_TEST_HELPER.reader.readTree(response.content) as ArrayNode

                assertEquals(widgets.size, rootNode.size())

                widgets.zip(rootNode).forEach { (widget, node) ->
                    assertEquals(widget.id, node.at("/id").intValue())
                    assertEquals(widget.name, node.at("/name").textValue())
                }
            }
        }
    }
}
