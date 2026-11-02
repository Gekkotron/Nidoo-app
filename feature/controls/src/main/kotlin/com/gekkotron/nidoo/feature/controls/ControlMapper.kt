package com.gekkotron.nidoo.feature.controls

import com.gekkotron.nidoo.feature.controls.model.ControlAction
import com.gekkotron.nidoo.feature.controls.model.ControlModel
import com.gekkotron.nidoo.feature.controls.model.ControlStatus
import com.gekkotron.nidoo.feature.controls.model.ControlType
import com.gekkotron.nidoo.feature.widget.Failure
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Command
import com.gekkotron.nidoo.model.Entity
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.model.UNASSIGNED_AREA
import kotlin.math.roundToInt

object ControlMapper {
    fun eligible(entity: Entity): Boolean =
        (entity.kind == Kind.LIGHT || entity.kind == Kind.SWITCH) && !entity.ui.hidden && entity.partOf == null

    fun model(entity: Entity, state: EntityState?, areas: List<Area>, status: ControlStatus = ControlStatus.OK): ControlModel {
        val dimmable = entity.kind == Kind.LIGHT && entity.capabilities.brightness
        val value = state?.value
        val on = when (value) {
            is StateValue.Light -> value.on
            is StateValue.OnOff -> value.on
            else -> false
        }
        val percent = (value as? StateValue.Light)?.brightness?.takeIf { dimmable }?.let { (it * 100 / 255f).roundToInt() }
        val zone = if (entity.area == UNASSIGNED_AREA) null else areas.firstOrNull { it.id == entity.area }?.name
        val effective = if (status == ControlStatus.OK && state?.available == false) ControlStatus.UNAVAILABLE else status
        return ControlModel(
            id = entity.uniqueId,
            title = entity.name,
            zone = zone,
            type = when {
                entity.kind == Kind.LIGHT -> ControlType.LIGHT
                entity.deviceClass == "outlet" -> ControlType.OUTLET
                else -> ControlType.SWITCH
            },
            dimmable = dimmable,
            on = on,
            brightnessPercent = percent,
            status = effective,
        )
    }

    fun status(failure: Failure): ControlStatus = when (failure) {
        Failure.OFFLINE -> ControlStatus.OFFLINE
        Failure.AUTH_FAILED -> ControlStatus.AUTH_FAILED
        Failure.NOT_CONFIGURED -> ControlStatus.NOT_CONFIGURED
    }

    fun command(model: ControlModel, action: ControlAction): Command = when (action) {
        is ControlAction.SetOn -> if (action.on) Command.TurnOn() else Command.TurnOff
        is ControlAction.SetBrightness -> {
            val brightness = (action.percent * 255f / 100f).roundToInt().coerceIn(0, 255)
            if (brightness == 0) Command.TurnOff else Command.TurnOn(brightness = brightness)
        }
    }
}
