package org.mpierce.ktordemo

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.inject.Inject
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.async
import org.jooq.DSLContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class WordEndpoints @Inject constructor(app: Application, jooq: DSLContext, daoFactory: DaoFactory) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(WordEndpoints::class.java)
    }

    init {
        app.routing {
            get("/words/id/{id}") {
                // Could also use a typed location to avoid the string-typing https://ktor.io/samples/locations.html
                val id = call.parameters["id"]!!.toInt()

                // just to show we can, we'll run this query async style and get back a `Deferred<Word>`
                val deferred = async {
                    // use a method reference for "the thing that makes the dao I want for this transaction"
                    jooq.txnWithDao(daoFactory::wordDao) {
                        it.getWord(id)
                    }
                }

                // sql request is in progress on the IO dispatcher
                logger.debug("Loading word $id")

                // wait for the query to finish
                when (val w = deferred.await()) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    else -> call.respond(w)
                }
            }

            put("words/id/{id}") {
                val id = call.parameters["id"]!!.toInt()
                val newWord = call.parameters["word"]!!.toString()

                // this time, no `async`, and thus the return type is `Word`, not `Deferred<Word>`
                val word = jooq.txnWithDao(daoFactory::wordDao) {
                    it.updateWord(id, newWord)
                }

                call.respond(word)
            }

            get("/words/all") {
                val words = jooq.txnWithDao(daoFactory::wordDao) {
                    it.getAllWords()
                }

                call.respond(words)
            }

            post("/words") {
                val req = call.receive<NewWordRequest>()

                val result = jooq.txnWithDao(daoFactory::wordDao) {
                    it.createWord(req.name)
                }

                call.respond(result)
            }
        }
    }
}

private class NewWordRequest(@JsonProperty("word") val name: String)
