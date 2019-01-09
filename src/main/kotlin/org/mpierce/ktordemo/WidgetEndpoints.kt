package org.mpierce.ktordemo

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.inject.Inject
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Instead of declaring endpoint logic inline, we can also do it with a class with DI by Guice.
 */
class WidgetEndpoints @Inject constructor(app: Application, jooq: DSLContext, daoFactory: DaoFactory) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(WidgetEndpoints::class.java)
    }

    init {
        app.routing {
            get("/widgets/id/{id}") {
                // Could also use a typed location to avoid the string-typing https://ktor.io/samples/locations.html
                val id = call.parameters["id"]!!.toInt()

                // for no particular reason, we'll run this query async style
                val deferred = async(Dispatchers.IO) {
                    jooq.transactionResult { txn ->
                        daoFactory.widgetDao(txn.dsl()).getWidget(id)
                    }
                }

                // sql request is in progress on the IO dispatcher

                logger.debug("Loading widget $id")

                // wait for the query to finish
                when (val w = deferred.await()) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    else -> call.respond(w)
                }
            }
            get("/widgets/all") {
                val widgets = withContext(Dispatchers.IO) {
                    jooq.transactionResult { txn ->
                        daoFactory.widgetDao(txn.dsl()).getAllWidgets()
                    }
                }
                call.respond(widgets)
            }
            post("/widgets") {
                val req = call.receive<NewWidgetRequest>()

                val result = withContext(Dispatchers.IO) {
                    jooq.transactionResult { txn ->
                        daoFactory.widgetDao(txn.dsl()).createWidget(req.name)
                    }
                }

                call.respond(result)
            }
        }
    }
}

/**
 * Deserialized from POSTed JSON.
 */
private data class NewWidgetRequest(@JsonProperty("name") val name: String)
