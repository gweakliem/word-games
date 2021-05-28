package org.mpierce.ktordemo.cli

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import com.google.inject.Stage
import com.natpryce.konfig.Configuration
import com.natpryce.konfig.ConfigurationProperties
import com.natpryce.konfig.EmptyConfiguration
import com.natpryce.konfig.EnvironmentVariables
import com.natpryce.konfig.overriding
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.jackson.JacksonConverter
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.mpierce.guice.warmup.GuiceWarmup
import org.mpierce.ktordemo.DaoFactoryModule
import org.mpierce.ktordemo.HttpServerConfig
import org.mpierce.ktordemo.SqlDaoFactory
import org.mpierce.ktordemo.WidgetEndpoints
import org.mpierce.ktordemo.buildHikariConfig
import org.mpierce.ktordemo.fromDirectory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class RunServer(private val start: Instant) : CliktCommand() {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val config by option(help = "Directory of properties files to read").path()

    override fun run() {
        // Help the service start up as fast as possible by initializing a few slow things in different
        // threads. If threading is too much complexity, just remove the threads and do things serially.
        val jackson = CompletableFuture.supplyAsync { configuredObjectMapper() }

        // This is totally optional but it helps the service start faster by having classes already loaded
        // by the time they're needed.
        val otherWarmupFutures: List<Future<*>> = listOf(
            CompletableFuture.supplyAsync { GuiceWarmup.warmUp() }
        )

        val config = buildConfig(config)

        val jooq = CompletableFuture.supplyAsync { buildJooq(configuredDataSource(config)) }

        val server = embeddedServer(Netty, port = HttpServerConfig(config).httpPort) {
            // add some built-in ktor features
            install(StatusPages) {
                // log when handling a request ends up throwing
                exception<Throwable> { cause -> logger.warn("Unhandled exception", cause) }
            }
            install(CallLogging) {
                // produce some request logs
                level = Level.INFO
            }

            configureJackson(this, jackson.get())
            setupGuice(
                this,
                JooqModule(jooq.get()),
                DaoFactoryModule(SqlDaoFactory())
            )
            otherWarmupFutures.forEach { it.get() }
            logger.info("Server initialized in ${Duration.between(start, Instant.now())}")
        }
        server.start(wait = true)
    }
}

fun buildConfig(configDir: Path?) =
    EnvironmentVariables() overriding
        (configDir?.let { ConfigurationProperties.fromDirectory(it) } ?: EmptyConfiguration)

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

fun configuredDataSource(config: Configuration) =
    HikariDataSource(buildHikariConfig(config, "KTOR_DEMO_DB_"))

fun buildJooq(hikariDataSource: HikariDataSource): DSLContext {
    return DSL.using(
        hikariDataSource,
        SQLDialect.POSTGRES,
        Settings()
            // we're using Postgres, so we can use INSERT ... RETURNING to get db-created column values on new
            // rows without a separate query
            .withReturnAllOnUpdatableRecord(true)
    )
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
        binder().disableCircularProxies()
    }
}

fun setupGuice(app: Application, vararg modules: Module): Injector {
    return Guice.createInjector(
        Stage.PRODUCTION,
        object : AbstractModule() {
            override fun configure() {
                modules.forEach { m -> install(m) }

                bind(Application::class.java).toInstance(app)

                // endpoints get bound eagerly so routes are set up
                listOf(WidgetEndpoints::class.java)
                    .forEach { bind(it).asEagerSingleton() }

                install(GuiceConfigModule())
            }
        }
    )
}
