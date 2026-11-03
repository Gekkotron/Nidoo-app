package com.gekkotron.nidoo.history

/** The hub's entity id (its `unique_id`), as sent to the `/api/v1/history/...` routes under `entity=`. */
data class HistoryRef(val id: String)

/**
 * A window of history, with the hub's wire names; [ttlMillis] is how long a cached answer stays fresh.
 *
 * [bucketed] ranges span days: each point is a mean/min/max bucket (so the chart draws the min/max band),
 * and a kWh meter can show daily bars. The shorter ones are close to raw samples.
 */
enum class HistoryRange(val query: String, val ttlMillis: Long, val bucketed: Boolean) {
    H1("1h", 60_000, bucketed = false),
    H6("6h", 60_000, bucketed = false),
    H24("24h", 60_000, bucketed = false),
    D7("7d", 600_000, bucketed = true),
    D30("30d", 600_000, bucketed = true),
    Y1("1y", 600_000, bucketed = true),
}

enum class EnergyKind(val query: String) {
    COUNTER("counter"),
    POWER("power"),
}

data class EnergyScale(val kind: EnergyKind, val factor: Double) {
    companion object {
        /** kWh = what the service returns × [factor]; the service integrates watts and sums the meter's own unit (spec §3.3). */
        fun of(unit: String?): EnergyScale? = when (unit?.trim()) {
            "kWh" -> EnergyScale(EnergyKind.COUNTER, 1.0)
            "Wh" -> EnergyScale(EnergyKind.COUNTER, 0.001)
            "W" -> EnergyScale(EnergyKind.POWER, 1.0)
            "kW" -> EnergyScale(EnergyKind.POWER, 1000.0)
            else -> null
        }
    }
}

data class HistoryPoint(val t: Long, val mean: Double, val min: Double, val max: Double)

data class Series(val windowSeconds: Long, val points: List<HistoryPoint>)

data class DayTotal(val t: Long, val kwh: Double)

data class DailyTotals(val days: List<DayTotal>)

sealed interface HistoryResult<out T> {
    data class Ok<T>(val value: T) : HistoryResult<T>
    data object NotRecorded : HistoryResult<Nothing>
    data object NotAllowed : HistoryResult<Nothing>
    data class Unavailable(val reason: String) : HistoryResult<Nothing>
}
