package org.mpierce.ktordemo

import com.fasterxml.jackson.annotation.JsonProperty
import org.jooq.Configuration
import org.jooq.DSLContext
import org.mpierce.ktordemo.jooq.Tables.WIDGETS
import org.mpierce.ktordemo.jooq.tables.records.WidgetsRecord
import java.time.Instant

class WidgetDao(private val txnContext: DSLContext) {

    constructor(config: Configuration) : this(config.dsl())

    fun getWidget(id: Int): Widget? {
        val r = txnContext.selectFrom(WIDGETS)
                .where(WIDGETS.ID.eq(id))
                .fetchOne()

        return r?.let { Widget(it) }
    }

    fun getAllWidgets(): List<Widget> {
        return txnContext.fetch(WIDGETS)
                .map { r -> Widget(r) }
                .toList()
    }

    fun createWidget(name: String): Widget {
        val result = txnContext.insertInto(WIDGETS, WIDGETS.NAME)
                .values(name)
                .returning()
                .fetchOne()

        return Widget(result)
    }
}

/**
 * Since this is such a simple model, we'll let it be serialized directly.
 */
data class Widget(@JsonProperty("id") val id: Int,
                  @JsonProperty("name") val name: String,
                  @JsonProperty("createdAt") val createdAt: Instant) {
    constructor(r: WidgetsRecord) : this(r.id, r.name, r.createdAt.toInstant())
}
