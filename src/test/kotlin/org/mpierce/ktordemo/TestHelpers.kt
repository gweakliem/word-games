package org.mpierce.ktordemo

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.google.inject.Injector
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.Application
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.withTestApplication
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.mpierce.ktordemo.jooq.Tables
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference


class DbTestHelper : Closeable {
    private val dataSource: HikariDataSource = buildDataSource(DataSourceConfig(
            dataSourceClassName,
            "ktor-demo-test",
            "ktor-demo-test",
            2,
            1,
            connInitSql,
            mapOf("databaseName" to "ktor-demo-test",
                    "portNumber" to "25432",
                    "serverName" to "localhost")
    ))

    val dslContext: DSLContext

    init {
        dslContext = buildJooqDsl(dataSource)
    }

    fun deleteAllRows() {
        dslContext.transaction { c ->
            c.dsl().apply {
                batch(
                        deleteFrom(Tables.WIDGETS)
                        // other tables as needed
                )
                        .execute()
            }
        }
    }

    override fun close() {
        dslContext.close()
        dataSource.close()
    }
}

class MemoryDaoFactory : DaoFactory {
    // need to re-use the same instance to avoid throwing away data
    private val widgetDao = MemoryWidgetDao()

    override fun widgetDao(txnContext: DSLContext): WidgetDao = widgetDao
}

/**
 * fake dsl -- memory dao impls don't ever actually use a db connection
 */
fun fakeJooqDsl(): DSLContext {
    return DSL.using(MockConnection(MockDataProvider {
        throw UnsupportedOperationException("should never be called")
    }))
}

/**
 * A helper to allow you to run tests while also having access to the injector used when initializing the app
 */
fun <R> withInMemoryTestApp(testBlock: TestApplicationEngine.(Injector) -> R) {
    val injectorRef = AtomicReference<Injector>()
    val appInit: Application.() -> Unit = {
        injectorRef.set(setUpAppWithInMemoryPersistence(this))
    }

    withTestApplication(appInit) {
        val injector = injectorRef.get()!!

        testBlock(injector)
    }
}

fun setUpAppWithInMemoryPersistence(app: Application): Injector {
    val injector = setupGuice(
            app,
            JooqModule(fakeJooqDsl()),
            DaoFactoryModule(MemoryDaoFactory())
    )

    configureJackson(app, DEFAULT_JSON_TEST_HELPER.mapper)

    return injector
}

/**
 * Set up a ktor application to use in-memory persistence.
 */
fun Application.testAppSetup() {
    setUpAppWithInMemoryPersistence(this)
}

/**
 * Create one and cache it because object mappers take a while to build.
 *
 * Lazy to speed up tests that don't need Jackson.
 */
val DEFAULT_JSON_TEST_HELPER: JsonTestHelper by lazy { JsonTestHelper(configuredObjectMapper()) }

class JsonTestHelper(internal val mapper: ObjectMapper) {

    internal val reader: ObjectReader = mapper.reader()

    fun assertJsonStrEquals(expected: String, entity: String) {
        val expectedNode = reader.forType(JsonNode::class.java).readValue<JsonNode>(expected)
        val actualNode = reader.forType(JsonNode::class.java).readValue<JsonNode>(entity)

        assertEquals(expectedNode, actualNode)
    }
}
