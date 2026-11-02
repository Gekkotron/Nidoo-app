package com.gekkotron.nidoo.feature.controls.model

enum class ControlType { LIGHT, SWITCH, OUTLET }

enum class ControlStatus { OK, UNAVAILABLE, NOT_FOUND, OFFLINE, AUTH_FAILED, NOT_CONFIGURED, NO_RESPONSE }

data class ControlModel(
    val id: String,
    val title: String,
    val zone: String?,
    val type: ControlType,
    val dimmable: Boolean,
    val on: Boolean,
    val brightnessPercent: Int?,
    val status: ControlStatus,
)

sealed interface ControlAction {
    data class SetOn(val on: Boolean) : ControlAction

    data class SetBrightness(val percent: Float) : ControlAction
}
