package com.gekkotron.nidoo.dashboards

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** The id of the app's own House dashboard. The hub refuses it as a dashboard id. */
const val HOUSE_DASHBOARD_ID = "house"

data class Dashboard(
    val id: String,
    val name: String,
    val order: Long,
    val showHeader: Boolean,
    val widgets: List<Widget>,
)

data class Widget(
    val id: String,
    val kind: WidgetKind,
    val span: WidgetSpan,
    val config: WidgetConfig,
    val pos: Cell?,
)

data class Cell(val row: Int, val col: Int)

enum class WidgetKind(val wire: String?) {
    LIGHT("light"),
    COVER("cover"),
    CLIMATE("climate"),
    LOCK("lock"),
    SENSOR("sensor"),
    ROOM_GROUP("room_group"),
    FLOW_BUTTON("flow_button"),
    ENERGY_STAT("energy_stat"),
    HISTORY_CHART("history_chart"),
    CAMERA("camera"),
    WATER_HEATER("water_heater"),
    UNKNOWN(null),
}

enum class WidgetSpan(val wire: String?, val cols: Int, val rows: Int) {
    S1X1("1x1", 1, 1),
    S2X1("2x1", 2, 1),
    S2X2("2x2", 2, 2),
    S1X2("1x2", 1, 2),
    S4X2("4x2", 4, 2),
    S6X3("6x3", 6, 3),
    UNKNOWN(null, 1, 1),
}

enum class WidgetRange(val wire: String?) {
    H1("1h"),
    H6("6h"),
    H24("24h"),
    D7("7d"),
    D30("30d"),
    UNKNOWN(null),
}

/** Flat on purpose: the hub's config is untagged; each kind reads its own fields. */
data class WidgetConfig(
    val entityId: String? = null,
    val areaId: String? = null,
    val flowId: String? = null,
    val metric: String? = null,
    val cameras: List<String> = emptyList(),
    val range: WidgetRange? = null,
    val title: String? = null,
)

@Serializable
private data class DashboardDto(
    val name: String,
    val order: Long? = null,
    @SerialName("show_header") val showHeader: Boolean? = null,
    val widgets: List<JsonElement> = emptyList(),
)

object DashboardJson {
    private val json = Json { ignoreUnknownKeys = true }

    /** Throws when the payload is unusable (not JSON, or no name). The topic [id] wins over the payload's. */
    fun decode(id: String, payload: String): Dashboard {
        val dto = json.decodeFromString<DashboardDto>(payload)
        val widgets = dto.widgets.mapIndexedNotNull { i, el -> (el as? JsonObject)?.let { widget(i, it) } }
        return Dashboard(id, dto.name, dto.order ?: Long.MAX_VALUE, dto.showHeader ?: false, widgets)
    }

    private fun widget(index: Int, o: JsonObject): Widget? = runCatching {
        val kind = o.str("kind")
        val span = o.str("span")
        val pos = (o["pos"] as? JsonObject)?.let { p ->
            val row = (p["row"] as? JsonPrimitive)?.intOrNull
            val col = (p["col"] as? JsonPrimitive)?.intOrNull
            if (row != null && col != null && row >= 0 && col >= 0) Cell(row, col) else null
        }
        Widget(
            id = o.str("id") ?: "#$index",
            kind = WidgetKind.entries.firstOrNull { it.wire != null && it.wire == kind } ?: WidgetKind.UNKNOWN,
            span = WidgetSpan.entries.firstOrNull { it.wire != null && it.wire == span } ?: WidgetSpan.UNKNOWN,
            config = (o["config"] as? JsonObject)?.let(::config) ?: WidgetConfig(),
            pos = pos,
        )
    }.getOrNull()

    private fun config(c: JsonObject): WidgetConfig {
        val range = c.str("range")
        return WidgetConfig(
            entityId = c.str("entity_id"),
            areaId = c.str("area_id"),
            flowId = c.str("flow_id"),
            metric = c.str("metric"),
            cameras = (c["cameras"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
            range = range?.let { r -> WidgetRange.entries.firstOrNull { it.wire == r } ?: WidgetRange.UNKNOWN },
            title = c.str("title")?.takeIf { it.isNotBlank() },
        )
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
