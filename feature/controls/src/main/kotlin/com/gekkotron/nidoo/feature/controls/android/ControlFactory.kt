package com.gekkotron.nidoo.feature.controls.android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.templates.ControlButton
import android.service.controls.templates.ControlTemplate
import android.service.controls.templates.RangeTemplate
import android.service.controls.templates.ToggleRangeTemplate
import android.service.controls.templates.ToggleTemplate
import androidx.annotation.RequiresApi
import com.gekkotron.nidoo.feature.controls.R
import com.gekkotron.nidoo.feature.controls.model.ControlModel
import com.gekkotron.nidoo.feature.controls.model.ControlStatus
import com.gekkotron.nidoo.feature.controls.model.ControlType

@RequiresApi(30)
class ControlFactory(private val context: Context) {
    fun stateless(model: ControlModel): Control =
        Control.StatelessBuilder(model.id, appIntent(model))
            .setTitle(model.title)
            .setSubtitle(model.zone ?: "")
            .apply { model.zone?.let { setZone(it) } }
            .setDeviceType(deviceType(model.type))
            .build()

    fun stateful(model: ControlModel): Control =
        Control.StatefulBuilder(model.id, appIntent(model))
            .setTitle(model.title)
            .setSubtitle(model.zone ?: "")
            .apply { model.zone?.let { setZone(it) } }
            .setDeviceType(deviceType(model.type))
            .setStatus(
                when (model.status) {
                    ControlStatus.OK -> Control.STATUS_OK
                    ControlStatus.NOT_FOUND -> Control.STATUS_NOT_FOUND
                    else -> Control.STATUS_ERROR
                },
            )
            .setStatusText(statusText(model))
            .setControlTemplate(template(model))
            .apply { if (Build.VERSION.SDK_INT >= 33) setAuthRequired(false) }
            .build()

    private fun template(model: ControlModel): ControlTemplate {
        if (model.status == ControlStatus.NOT_FOUND) return ControlTemplate.getNoTemplateObject()
        val button = ControlButton(model.on, context.getString(R.string.controls_action))
        if (!model.dimmable) return ToggleTemplate("${model.id}_toggle", button)
        val value = if (model.on) (model.brightnessPercent ?: 100) else 0
        return ToggleRangeTemplate(
            "${model.id}_toggle",
            button,
            RangeTemplate("${model.id}_range", 0f, 100f, value.toFloat(), 1f, "%.0f%%"),
        )
    }

    private fun statusText(model: ControlModel): String = when (model.status) {
        ControlStatus.OK -> when {
            model.dimmable && model.on -> "${model.brightnessPercent ?: 100} %"
            model.on -> context.getString(R.string.controls_on)
            else -> context.getString(R.string.controls_off)
        }
        ControlStatus.UNAVAILABLE, ControlStatus.NOT_FOUND -> context.getString(R.string.controls_unavailable)
        ControlStatus.OFFLINE -> context.getString(R.string.controls_offline)
        ControlStatus.AUTH_FAILED -> context.getString(R.string.controls_auth_failed)
        ControlStatus.NOT_CONFIGURED -> context.getString(R.string.controls_not_configured)
        ControlStatus.NO_RESPONSE -> context.getString(R.string.controls_no_response)
    }

    private fun deviceType(type: ControlType): Int = when (type) {
        ControlType.LIGHT -> DeviceTypes.TYPE_LIGHT
        ControlType.SWITCH -> DeviceTypes.TYPE_SWITCH
        ControlType.OUTLET -> DeviceTypes.TYPE_OUTLET
    }

    private fun appIntent(model: ControlModel): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, model.id.hashCode(), launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
