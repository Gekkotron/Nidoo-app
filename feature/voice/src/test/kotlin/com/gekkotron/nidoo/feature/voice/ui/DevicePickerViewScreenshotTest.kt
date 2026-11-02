package com.gekkotron.nidoo.feature.voice.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gekkotron.nidoo.designsystem.NidooTheme
import com.gekkotron.nidoo.designsystem.ThemeChoice
import com.gekkotron.nidoo.feature.voice.PickStep
import com.gekkotron.nidoo.feature.voice.VoiceSessionState
import com.gekkotron.nidoo.model.CodeFormat
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.voice.VoiceArea
import com.gekkotron.nidoo.voice.VoiceCatalog
import com.gekkotron.nidoo.voice.VoiceEntity
import com.gekkotron.nidoo.voice.VoiceLang
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal fun pickerEntity(id: String, name: String, kind: Kind, area: String) = VoiceEntity(
    uniqueId = id, displayName = name, kind = kind, areaId = area, deviceClass = null,
    brightness = false, position = false, codeFormat = CodeFormat.NUMBER, alarmModes = emptySet(),
    codeArmRequired = false, codeDisarmRequired = false,
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fr")
class DevicePickerViewScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val catalog = VoiceCatalog(
        VoiceLang.FR,
        listOf(
            pickerEntity("l1", "Plafonnier", Kind.LIGHT, "salon"),
            pickerEntity("c1", "Volet baie", Kind.COVER, "salon"),
            pickerEntity("l2", "Chevet", Kind.LIGHT, "chambre"),
            pickerEntity("s1", "Température", Kind.SENSOR, "chambre"),
        ),
        listOf(VoiceArea("salon", "Salon", false, "living"), VoiceArea("chambre", "Chambre", false, "bedroom")),
    )
    private val hints = mapOf("l1" to "60 %", "c1" to "Ouvert", "s1" to "21°")

    private fun picking(filter: String) =
        VoiceSessionState.Picking(PickStep.Device, "allume", filter, null, emptyList())

    private fun show(choice: ThemeChoice, filter: String, onPick: (String) -> Unit = {}) = rule.setContent {
        NidooTheme(choice) {
            Column {
                DevicePickerView(picking(filter), catalog, hints, onPick, {}, {})
            }
        }
    }

    @Test fun devicePicker_default_light() {
        var picked: String? = null
        show(ThemeChoice.IVOIRE, "") { picked = it }
        rule.onNodeWithText("Salon").assertExists()
        rule.onNodeWithText("Chambre").assertExists()
        rule.onNodeWithText("Lumière · 60 %").assertExists()
        rule.onNodeWithText("Capteur · 21°").assertExists()
        rule.onNodeWithText("Plafonnier").performClick()
        assert(picked == "l1")
    }

    @Test fun devicePicker_default_dark() {
        show(ThemeChoice.NIGHT_GLASS, "")
        rule.onNodeWithText("Volet baie").assertExists()
        rule.onNodeWithText("Retour").assertExists()
    }

    @Test fun devicePicker_filtered_light() {
        show(ThemeChoice.IVOIRE, "lum")
        rule.onNodeWithText("Plafonnier").assertExists()
        rule.onNodeWithText("Chevet").assertExists()
        rule.onNodeWithText("Volet baie").assertDoesNotExist()
        rule.onNodeWithText("Température").assertDoesNotExist()
    }

    @Test fun devicePicker_no_match_shows_empty_state() {
        show(ThemeChoice.IVOIRE, "zzz")
        rule.onNodeWithText("Aucun résultat").assertExists()
    }
}
