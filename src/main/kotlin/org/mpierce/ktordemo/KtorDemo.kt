import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Stage
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.async
import org.apache.commons.configuration.EnvironmentConfiguration
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.mpierce.ktordemo.GetWidgetEndpoint
import org.mpierce.ktordemo.KtorDemoConfig
import org.mpierce.ktordemo.jooq.Tables.WIDGETS
import org.mpierce.ktordemo.jooq.tables.records.WidgetsRecord
import org.skife.config.CommonsConfigSource
import org.skife.config.ConfigurationObjectFactory
import java.time.Instant

fun main(args: Array<String>) {
    // If you need config types available via Guice, see the config-inject library
    val config = ConfigurationObjectFactory(CommonsConfigSource(EnvironmentConfiguration()))
            .build(KtorDemoConfig::class.java)

    val hikariConfig = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://${config.dbIp()}:${config.dbPort()}/ktor-demo"
        username = config.dbUser()
        password = config.dbPassword()
    }

    // For demonstration's sake, we'll let the jooq context be a local var to be used
    // in the endpoint closures below, but also passed to Guice to be used in endpoint classes
    val jooq = DSL.using(HikariDataSource(hikariConfig), SQLDialect.POSTGRES)

    val server = embeddedServer(Netty, port = 8080) {
        setupGuice(jooq)
        install(ContentNegotiation) {
            jackson {
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
        routing {
            get("/widgets/all") {
                val widgets = async {
                    jooq.fetch(WIDGETS)
                            .map({ WidgetResponse.fromRecord(it) })
                            .toList()
                }
                call.respond(widgets.await())
            }
            post("/widgets") {
                val req = call.receive<WidgetPost>()

                val result = async {
                    jooq.insertInto(WIDGETS, WIDGETS.NAME)
                            .values(req.name)
                            .returning(WIDGETS.ID, WIDGETS.CREATED_AT)
                            .fetchOne()
                }.await()

                call.respond(WidgetResponse(result.getValue(WIDGETS.ID), req.name,
                        result.getValue(WIDGETS.CREATED_AT).toInstant()))
            }
        }
    }
    server.start(wait = true)
}

fun Application.setupGuice(jooq: DSLContext) {
    // We don't actually need the injector itself; the necessary linkage happens in the
    // ctor for endpoints. More complex usages may wish to hang on to the injector to
    // create child injectors, etc. See https://ktor.io/samples/guice.html.
    Guice.createInjector(Stage.PRODUCTION,
            object : AbstractModule() {
                override fun configure() {
                    bind(DSLContext::class.java).toInstance(jooq)

                    bind(Application::class.java).toInstance(this@setupGuice)
                    bind(GetWidgetEndpoint::class.java).asEagerSingleton()

                    // sane Guice config
                    binder().requireAtInjectOnConstructors()
                    binder().requireExactBindingAnnotations()
                    binder().requireExplicitBindings()
                }
            })
}

/**
 * Deserialized from POSTed JSON.
 */
data class WidgetPost(@JsonProperty("name") val name: String)

/**
 * Serialized into a JSON response.
 */
data class WidgetResponse(@JsonProperty("id") val id: Int,
                          @JsonProperty("name") val name: String,
                          @JsonProperty("createdAt") val createdAt: Instant) {
    companion object {
        /**
         * Build a response from a jooq Record.
         */
        fun fromRecord(r: WidgetsRecord): WidgetResponse = WidgetResponse(
                r.id,
                r.name,
                r.createdAt.toInstant()
        )
    }
}
