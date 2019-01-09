package org.mpierce.ktordemo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import com.google.inject.Stage
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.jackson.JacksonConverter
import io.ktor.jackson.jackson
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.apache.commons.configuration.EnvironmentConfiguration
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.skife.config.CommonsConfigSource
import org.skife.config.ConfigurationObjectFactory
import org.slf4j.bridge.SLF4JBridgeHandler

fun main(args: Array<String>) {
    // Use SLF4J for JUL
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()

    val config = ConfigurationObjectFactory(CommonsConfigSource(EnvironmentConfiguration()))
            .build(KtorDemoConfig::class.java)

    // For demonstration's sake, we'll let the jooq context be a local var to be used
    // in the endpoint closures below, but also passed to Guice to be used in endpoint classes
    val jooq = DSL.using(
            buildDataSource(
                    config.dbIp(),
                    config.dbPort(),
                    "ktor-demo-dev",
                    config.dbUser(),
                    config.dbPassword(),
                    4,
                    1
            ),
            SQLDialect.POSTGRES)

    val server = embeddedServer(Netty, port = config.httpPort()) {
        setupGuice(
                this,
                JooqModule(jooq),
                DaoFactoryModule(SqlDaoFactory())
        )
        configureJackson(this, configuredObjectMapper())
    }
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

                    // sane Guice config
                    binder().requireAtInjectOnConstructors()
                    binder().requireExactBindingAnnotations()
                    binder().requireExplicitBindings()
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

class JooqModule(private val jooq: DSLContext) : AbstractModule() {
    override fun configure() {
        bind(DSLContext::class.java).toInstance(jooq)
    }
}

