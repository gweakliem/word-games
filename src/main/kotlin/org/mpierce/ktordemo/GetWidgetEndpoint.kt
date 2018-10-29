package org.mpierce.ktordemo

import com.google.inject.Inject
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.jooq.DSLContext
import org.mpierce.ktordemo.jooq.Tables

/**
 * Instead of declaring endpoint logic inline, we can also do it with a class with DI by Guice.
 */
class GetWidgetEndpoint @Inject constructor(app: Application, jooq: DSLContext) {
    init {
        app.routing {
            get("/widgets/id/{id}") {
                // Could also use a typed location to avoid the string-typing https://ktor.io/samples/locations.html
                val id = call.parameters["id"]!!.toInt()
                val r = async(Dispatchers.IO) {
                    jooq.selectFrom(Tables.WIDGETS)
                            .where(Tables.WIDGETS.ID.eq(id))
                            .fetchOne()
                }.await()
                when (r) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    else -> call.respond(WidgetResponse(r))
                }
            }
        }
    }
}
