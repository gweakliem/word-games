package org.mpierce.ktordemo

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import org.mpierce.ktordemo.jooq.tables.records.WidgetsRecord

/**
 * The way I've chosen to do it in this project is to have 2 implementations of the DAO: one backed by Postgres via
 * Jooq, and another for use in tests that don't care about a SQL database that is a naive in-memory implementation.
 * This allows tests that aren't testing SQL specifically to run much faster.
 */
interface WidgetDao {
    /**
     * @return the widget with that id, or null if not found
     */
    fun getWidget(id: Int): Widget?

    /**
     * @return All widgets in creation order (newest last).
     */
    fun getAllWidgets(): List<Widget>

    /**
     * @return the newly created Widget
     */
    fun createWidget(name: String): Widget

    /**
     * @return the updated Widget
     */
    fun updateWidgetName(id: Int, name: String): Widget

    /**
     * An example of a reporting-type query.
     *
     * @return a map of one letter strings to how many widgets had a name that started with that string
     */
    fun widgetNameFirstLetterCounts(): Map<String, Int>
}

/**
 * Since this is such a simple model, we'll let it be serialized directly.
 */
data class Widget(
    @JsonProperty("id") val id: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("createdAt") val createdAt: Instant
) {
    constructor(r: WidgetsRecord) : this(r.id, r.name, r.createdAt.toInstant())
}
