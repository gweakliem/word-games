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
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler
import org.slf4j.event.Level
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

object KtorDemo {
    private val logger = LoggerFactory.getLogger(KtorDemo::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val start = Instant.now()
        // Use SLF4J for java.util.logging
        SLF4JBridgeHandler.removeHandlersForRootLogger()
        SLF4JBridgeHandler.install()

        // Help the service start up as fast as possible by initializing a few slow things in different
        // threads. If threading is too much complexity, just remove the thread pool and do things serially.
        // Daemon threads are used so they won't keep the JVM up if the main thread dies from a startup error.
        val initPool = Executors.newCachedThreadPool(DaemonThreadFactory())
        val jackson = initPool.submit(Callable {
            configuredObjectMapper()
        })

        // This is totally optional but it helps the service start faster by having classes already loaded
        // by the time they're needed.
        val otherWarmupFutures = listOf(
            initPool.submit { GuiceWarmup.warmUp() }
        )

        // Here, we use commons-configuration's CompositeConfiguration and config-magic to let us combine different config
        // sources.
        val configPath = args.getOrElse(0) { throw RuntimeException("Must provide config dir as a CLI arg") }
        val config = EnvironmentVariables() overriding
            ConfigurationProperties.fromDirectory(Path.of(configPath))

        val jooq = initPool.submit(Callable {
            buildJooqDsl(HikariDataSource(buildHikariConfig(config, "KTOR_DEMO_DB")))
        })
        initPool.shutdown()

        val server = embeddedServer(Netty, port = KonfigHttpServerConfig(config).httpPort) {
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

fun buildJooqDsl(hikariDataSource: HikariDataSource): DSLContext {
    return DSL.using(hikariDataSource,
            SQLDialect.POSTGRES,
            Settings()
                    // we're using Postgres, so we can use INSERT ... RETURNING to get db-created column values on new
                    // rows without a separate query
                    .withReturnAllOnUpdatableRecord(true))
}

/**
 * Apply properties files in the provided directory to the compsite configuration in sorted order.
 *
 * Later files overwrite previous files. In other words, data in 02-bar.properties will take precedence over
 * 01-foo.properties.
 */
fun ConfigurationProperties.Companion.fromDirectory(configDir: Path): Configuration {
    return Files.newDirectoryStream(configDir)
        .filter { p -> p.fileName.toString().endsWith("properties") }
        .asSequence()
        .sorted()
        // TODO explicitly use UTF-8 once https://github.com/npryce/konfig/pull/46 lands
        .map { p -> fromFile(p.toFile()) }
        .fold(EmptyConfiguration as Configuration) { acc, config ->
            com.natpryce.konfig.Override(config, acc)
        }
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

/**
 * Makes threads the normal way, except that they're marked as daemon threads so they won't keep the JVM alive.
 */
class DaemonThreadFactory : ThreadFactory {
    private val delegate = Executors.defaultThreadFactory()

    override fun newThread(r: Runnable): Thread {
        return delegate.newThread(r).apply { isDaemon = true }
    }
}
