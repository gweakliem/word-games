package org.mpierce.ktordemo

import com.fasterxml.jackson.annotation.JsonProperty
import org.mpierce.ktordemo.jooq.tables.records.WidgetsRecord
import java.time.Instant

/**
 * The way I've chosen to do it in this project is to have 2 implementations of the DAO: one backed by Postgres via
 * Jooq, and another for use in tests that don't care about a SQL database that is a naive in-memory implementation.
 * This allows tests that aren't testing SQL specifically to run much faster.
 */
interface WidgetDao {
    fun getWidget(id: Int): Widget?
    /**
     * @return All widgets in creation order (newest last).
     */
    fun getAllWidgets(): List<Widget>
    fun createWidget(name: String): Widget
    fun updateWidgetName(id: Int, name: String): Widget
}

/**
 * Since this is such a simple model, we'll let it be serialized directly.
 */
data class Widget(@JsonProperty("id") val id: Int,
                  @JsonProperty("name") val name: String,
                  @JsonProperty("createdAt") val createdAt: Instant) {
    constructor(r: WidgetsRecord) : this(r.id, r.name, r.createdAt.toInstant())
}
