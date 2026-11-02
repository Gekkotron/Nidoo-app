package com.gekkotron.nidoo.feature.controls.android

import android.service.controls.Control
import android.service.controls.DeviceTypes
import android.service.controls.templates.ToggleRangeTemplate
import android.service.controls.templates.ToggleTemplate
import androidx.test.core.app.ApplicationProvider
import com.gekkotron.nidoo.feature.controls.model.ControlModel
import com.gekkotron.nidoo.feature.controls.model.ControlStatus
import com.gekkotron.nidoo.feature.controls.model.ControlType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ControlFactoryTest {
    private val factory = ControlFactory(ApplicationProvider.getApplicationContext())

    private fun model(
        type: ControlType = ControlType.LIGHT,
        dimmable: Boolean = true,
        on: Boolean = true,
        percent: Int? = 50,
        zone: String? = "Salon",
        status: ControlStatus = ControlStatus.OK,
    ) = ControlModel("id1", "Lamp", zone, type, dimmable, on, percent, status)

    @Test
    fun dimmable_light_uses_toggle_range() {
        val control = factory.stateful(model(percent = 50))
        val template = control.controlTemplate as ToggleRangeTemplate
        assertEquals(DeviceTypes.TYPE_LIGHT, control.deviceType)
        assertEquals(0f, template.range.minValue, 0f)
        assertEquals(100f, template.range.maxValue, 0f)
        assertEquals(50f, template.range.currentValue, 0f)
        assertEquals(Control.STATUS_OK, control.status)
    }

    @Test
    fun on_light_without_brightness_shows_full_range() {
        val on = factory.stateful(model(on = true, percent = null)).controlTemplate as ToggleRangeTemplate
        assertEquals(100f, on.range.currentValue, 0f)
        val off = factory.stateful(model(on = false, percent = null)).controlTemplate as ToggleRangeTemplate
        assertEquals(0f, off.range.currentValue, 0f)
    }

    @Test
    fun switch_uses_plain_toggle_and_outlet_type() {
        val control = factory.stateful(model(type = ControlType.OUTLET, dimmable = false, on = true, percent = null))
        val template = control.controlTemplate as ToggleTemplate
        assertEquals(DeviceTypes.TYPE_OUTLET, control.deviceType)
        assertTrue(template.isChecked)
    }

    @Test
    fun failure_statuses_are_errors_with_text() {
        val offline = factory.stateful(model(status = ControlStatus.OFFLINE))
        assertEquals(Control.STATUS_ERROR, offline.status)
        assertEquals("Offline", offline.statusText.toString())
        val missing = factory.stateful(model(status = ControlStatus.NOT_FOUND))
        assertEquals(Control.STATUS_NOT_FOUND, missing.status)
    }

    @Test
    fun zone_is_omitted_when_null() {
        val control = factory.stateless(model(zone = null))
        assertTrue(control.zone.isNullOrEmpty())
        assertEquals("", control.subtitle.toString())
    }
}
