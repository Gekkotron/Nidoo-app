package com.gekkotron.nidoo.feature.home.presentation

import com.gekkotron.nidoo.feature.home.resources.*
import org.jetbrains.compose.resources.StringResource

/** English entity labels published by discovery sources (Z2M, Tasmota, ESPHome…) → a translated string resource. */
object EntityLabels {
    /** trim, lowercase, '_' and '-' become spaces, runs of whitespace collapse to one space. */
    fun normalize(label: String): String =
        label.trim().lowercase().replace('_', ' ').replace('-', ' ').replace(Regex("\\s+"), " ")

    /** The string resource for [label], or null when the label is not a known one. */
    fun resource(label: String): StringResource? = map[normalize(label)]

    internal val keys: Set<String> get() = map.keys

    /** Every known normalised label with its resource, for preloading the translations. */
    internal val resources: Map<String, StringResource> get() = map

    private val map: Map<String, StringResource> = mapOf(
        "temperature" to Res.string.entity_label_temperature,
        "local temperature" to Res.string.entity_label_local_temperature,
        "humidity" to Res.string.entity_label_humidity,
        "pressure" to Res.string.entity_label_pressure,
        "battery" to Res.string.entity_label_battery,
        "battery low" to Res.string.entity_label_battery_low,
        "voltage" to Res.string.entity_label_voltage,
        "current" to Res.string.entity_label_current,
        "power" to Res.string.entity_label_power,
        "energy" to Res.string.entity_label_energy,
        "illuminance" to Res.string.entity_label_illuminance,
        "illuminance lux" to Res.string.entity_label_illuminance,
        "occupancy" to Res.string.entity_label_occupancy,
        "presence" to Res.string.entity_label_presence,
        "motion" to Res.string.entity_label_motion,
        "contact" to Res.string.entity_label_contact,
        "door" to Res.string.entity_label_door,
        "window" to Res.string.entity_label_window,
        "water leak" to Res.string.entity_label_water_leak,
        "smoke" to Res.string.entity_label_smoke,
        "gas" to Res.string.entity_label_gas,
        "carbon monoxide" to Res.string.entity_label_carbon_monoxide,
        "co2" to Res.string.entity_label_co2,
        "voc" to Res.string.entity_label_voc,
        "formaldehyde" to Res.string.entity_label_formaldehyde,
        "pm25" to Res.string.entity_label_pm25,
        "pm10" to Res.string.entity_label_pm10,
        "vibration" to Res.string.entity_label_vibration,
        "tamper" to Res.string.entity_label_tamper,
        "action" to Res.string.entity_label_action,
        "state" to Res.string.entity_label_state,
        "position" to Res.string.entity_label_position,
        "tilt" to Res.string.entity_label_tilt,
        "moving" to Res.string.entity_label_moving,
        "brightness" to Res.string.entity_label_brightness,
        "color temp" to Res.string.entity_label_color_temp,
        "soil moisture" to Res.string.entity_label_soil_moisture,
        "rain" to Res.string.entity_label_rain,
        "wind speed" to Res.string.entity_label_wind_speed,
        "noise" to Res.string.entity_label_noise,
        "alarm" to Res.string.entity_label_alarm,
        "lock" to Res.string.entity_label_lock,
        "valve" to Res.string.entity_label_valve,
        "heating" to Res.string.entity_label_heating,
        "cooling" to Res.string.entity_label_cooling,
        "running state" to Res.string.entity_label_running_state,
        "system mode" to Res.string.entity_label_system_mode,
        "preset" to Res.string.entity_label_preset,
        "fan mode" to Res.string.entity_label_fan_mode,
        "child lock" to Res.string.entity_label_child_lock,
        "led indication" to Res.string.entity_label_led_indication,
    )
}
