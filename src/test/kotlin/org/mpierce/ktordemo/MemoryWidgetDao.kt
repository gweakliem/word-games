package org.mpierce.ktordemo

import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * An in-memory implementation of WidgetDao to use when testing other logic that needn't use an actual database.
 */
class MemoryWidgetDao : WidgetDao {
    private val widgets = mutableMapOf<Int, Widget>()
    private val nextId = AtomicInteger()

    @Synchronized
    override fun getWidget(id: Int): Widget? = widgets[id]

    @Synchronized
    override fun getAllWidgets(): List<Widget> {
        return widgets.values.toList()
    }

    @Synchronized
    override fun createWidget(name: String): Widget {
        val w = Widget(nextId.getAndIncrement(), name, Instant.now())
        widgets[w.id] = w
        return w
    }

    @Synchronized
    override fun updateWidgetName(id: Int, name: String): Widget {
        val widget = widgets[id]!!.copy(name = name)
        widgets[id] = widget
        return widget
    }

    @Synchronized
    override fun widgetNameFirstLetterCounts(): Map<String, Int> {
        val prefix = { s: String -> s.substring(0, 1).toUpperCase(Locale.US) }
        return widgets
            .entries
            .sortedBy { it.value.name.let(prefix) }
            .groupBy { it.value.name.let(prefix) }
            .mapValues { (_, values) -> values.size }
    }
}
