package org.mpierce.ktordemo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Module
import com.google.inject.Provides
import com.google.inject.Singleton
import com.google.inject.Stage
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.jackson.JacksonConverter
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.apache.commons.configuration.EnvironmentConfiguration
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.skife.config.CommonsConfigSource
import org.skife.config.ConfigurationObjectFactory
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors

fun main(args: Array<String>) {
    val start = Instant.now()
    // Use SLF4J for java.util.logging
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()

    // Help the service start up as fast as possible by initializing a few slow things in different
    // threads. If using threads is confusing, just remove the thread pool and do things serially.
    val initPool = Executors.newCachedThreadPool()
    val jackson = initPool.submit(Callable<ObjectMapper> {
        configuredObjectMapper()
    })

    // This is totally optional but it helps the service start faster by having classes already loaded
    // by the time they're needed.
    val otherWarmupFutures = listOf(
            initPool.submit(::warmUpGuice)
    )

    // This loads config from environment variables, but this can be trivially replaced or augmented with files or
    // any other type of config you like
    val config = ConfigurationObjectFactory(CommonsConfigSource(EnvironmentConfiguration()))
            .build(KtorDemoConfig::class.java)

    val logger = LoggerFactory.getLogger("org.mpierce.ktordemo")

    val jooq = initPool.submit(Callable<DSLContext> {
        buildJooqDsl(buildDataSource(
                config.dbIp(),
                config.dbPort(),
                "ktor-demo-dev",
                config.dbUser(),
                config.dbPassword(),
                4,
                1
        ))
    })

    val server = embeddedServer(Netty, port = config.httpPort()) {
        // any other ktor features would be set up here
        configureJackson(this, jackson.get())
        setupGuice(
                this,
                JooqModule(jooq.get()),
                DaoFactoryModule(SqlDaoFactory())
        )
        otherWarmupFutures.forEach { it.get() }
        logger.info("Server initialized in ${Duration.between(start, Instant.now())}")
    }
    initPool.shutdown()
    server.start(wait = true)
}

fun setupGuice(app: Application, vararg modules: Module): Injector {
    return Guice.createInjector(Stage.PRODUCTION,
            object : AbstractModule() {
                override fun configure() {
                    modules.forEach { m -> install(m) }

                    bind(Application::class.java).toInstance(app)

                    // endpoints get bound eagerly so routes are set up
                    listOf(WidgetEndpoints::class.java)
                            .forEach { bind(it).asEagerSingleton() }

                    install(GuiceConfigModule())
                }
            })
}

fun configureJackson(app: Application, objectMapper: ObjectMapper) {
    app.install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }
}

fun configuredObjectMapper(): ObjectMapper {
    return ObjectMapper().apply {
        // Write dates in ISO8601
        setConfig(serializationConfig.withoutFeatures(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
        // Don't barf when deserializing json with extra stuff in it
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        // Kotlin support
        registerModule(KotlinModule())
        // Handle Java 8's new time types
        registerModule(JavaTimeModule())
    }
}

fun buildDataSource(ip: String, port: Int, dbName: String, user: String, pass: String,
                    connPoolMaxSize: Int, connPoolMinIdle: Int): HikariDataSource {
    val config = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://$ip:$port/$dbName"
        username = user
        password = pass
        isAutoCommit = false
        maximumPoolSize = connPoolMaxSize
        minimumIdle = connPoolMinIdle
        // per jdbc spec, driver uses tz from the host JVM. For local dev, this is lame, so we just always set UTC.
        // This means that casting a timestamp to a date (for grouping, for instance) will use UTC.
        connectionInitSql = "SET TIME ZONE 'UTC'"
    }
    return HikariDataSource(config)
}

fun buildJooqDsl(hikariDataSource: HikariDataSource): DSLContext {
    return DSL.using(hikariDataSource,
            SQLDialect.POSTGRES,
            Settings()
                    // we're using Postgres, so we can use INSERT ... RETURNING to get db-created column values on new
                    // rows without a separate query
                    .withReturnAllOnUpdatableRecord(true))
}

/**
 * Warm up Guice classes before we actually have the final set of modules available to inject.
 * This saves a few hundred ms for overall app startup.
 */
private fun warmUpGuice() {
    class Dependent
    @Suppress("unused")
    class Depender @Inject constructor(private val dependent: Dependent)

    // warming up Guice pays down about 200ms towards creating the final injector,
    // which would otherwise typically be the last thing to finish (on Macbook Pro)
    val injector = Guice.createInjector(Stage.PRODUCTION, object : AbstractModule() {
        override fun configure() {
            install(GuiceConfigModule())
            bind(Depender::class.java)
        }

        // use a provides method to warm up more reflection stuff
        @Provides
        @Singleton
        fun getDependent(): Dependent = Dependent()
    })

    injector.getInstance(Depender::class.java)
}

class JooqModule(private val jooq: DSLContext) : AbstractModule() {
    override fun configure() {
        bind(DSLContext::class.java).toInstance(jooq)
    }
}

/**
 * Sane base Guice config
 */
class GuiceConfigModule : AbstractModule() {
    override fun configure() {

        binder().requireAtInjectOnConstructors()
        binder().requireExactBindingAnnotations()
        binder().requireExplicitBindings()
    }
}
