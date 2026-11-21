package com.gekkotron.nidoo.feature.home.ui

import com.gekkotron.nidoo.feature.home.presentation.HomeUiBuilder
import com.gekkotron.nidoo.feature.home.presentation.HomeUiState
import com.gekkotron.nidoo.feature.home.presentation.RoomFilter
import com.gekkotron.nidoo.feature.home.testEntity
import com.gekkotron.nidoo.feature.home.withLivingHubGroups
import com.gekkotron.nidoo.model.Area
import com.gekkotron.nidoo.model.Capabilities
import com.gekkotron.nidoo.model.CoverState
import com.gekkotron.nidoo.model.EntityState
import com.gekkotron.nidoo.model.Kind
import com.gekkotron.nidoo.model.StateValue
import com.gekkotron.nidoo.transport.LinkState
import com.gekkotron.nidoo.transport.TransportMode
import java.util.Locale

/** A seven-room house for the Rooms and room-view screenshots (rooms design §7). */
object RoomsFixtures {
    private val areas = listOf(
        Area("living", "Living room"), Area("kitchen", "Kitchen"), Area("bedroom", "Bedroom"), Area("bathroom", "Bathroom"),
        Area("garage", "Garage"), Area("garden", "Garden"), Area("laundry", "Laundry"),
    )

    private fun temperature(id: String, area: String) = testEntity(id, Kind.SENSOR, "Temperature", area = area, deviceClass = "temperature", unit = "°C")
    private fun humidity(id: String, area: String) = testEntity(id, Kind.SENSOR, "Humidity", area = area, deviceClass = "humidity", unit = "%")

    private val house = withLivingHubGroups(
        listOf(
            testEntity("l_ceiling", Kind.LIGHT, "Ceiling", area = "living", capabilities = Capabilities(brightness = true)),
            testEntity("l_floor", Kind.LIGHT, "Floor lamp", area = "living"),
            testEntity("l_bay", Kind.COVER, "Bay window", area = "living", capabilities = Capabilities(position = true)),
            testEntity("l_side", Kind.COVER, "Side window", area = "living", capabilities = Capabilities(position = true)),
            testEntity("l_ac", Kind.CLIMATE, "Air conditioner", area = "living", capabilities = Capabilities(hvacModes = listOf("off", "cool"))),
            testEntity("l_tv", Kind.SWITCH, "TV plug", area = "living"),
            testEntity("l_speaker", Kind.MEDIA_PLAYER, "Speaker", area = "living"),
            temperature("l_temp", "living"), humidity("l_hum", "living"),
            testEntity("k_led", Kind.LIGHT, "Counter LED", area = "kitchen"),
            testEntity("k_shutter", Kind.COVER, "Shutter", area = "kitchen"),
            testEntity("k_coffee", Kind.SWITCH, "Coffee machine", area = "kitchen"),
            temperature("k_fridge", "kitchen"),
            testEntity("b_bedside", Kind.LIGHT, "Bedside", area = "bedroom"),
            testEntity("b_shutter", Kind.COVER, "Shutter", area = "bedroom"),
            temperature("b_temp", "bedroom"),
            testEntity("ba_light", Kind.LIGHT, "Mirror light", area = "bathroom"),
            temperature("ba_temp", "bathroom"), humidity("ba_hum", "bathroom"),
            testEntity("g_door", Kind.COVER, "Garage door", area = "garage", deviceClass = "garage"),
            testEntity("ga_light", Kind.LIGHT, "Terrace light", area = "garden"),
            temperature("ga_temp", "garden"),
            testEntity("la_washer", Kind.SWITCH, "Washer plug", area = "laundry"),
        ),
    )

    private val states = mapOf(
        "l_ceiling" to EntityState("l_ceiling", StateValue.Light(true, 200)),
        "l_floor" to EntityState("l_floor", StateValue.Light(true)),
        "l_bay" to EntityState("l_bay", StateValue.Cover(CoverState.OPEN, 100)),
        "l_side" to EntityState("l_side", StateValue.Cover(CoverState.CLOSED, 0)),
        "l_ac" to EntityState("l_ac", StateValue.Climate("cool", 22.0, 21.5)),
        "l_tv" to EntityState("l_tv", StateValue.OnOff(true)),
        "l_temp" to EntityState("l_temp", StateValue.Numeric(21.5)),
        "l_hum" to EntityState("l_hum", StateValue.Numeric(48.0)),
        "k_led" to EntityState("k_led", StateValue.Light(false)),
        "k_shutter" to EntityState("k_shutter", StateValue.Cover(CoverState.CLOSING, 60)),
        "k_fridge" to EntityState("k_fridge", StateValue.Numeric(4.1)),
        "b_bedside" to EntityState("b_bedside", StateValue.Light(false)),
        "b_shutter" to EntityState("b_shutter", StateValue.Cover(CoverState.CLOSED, 0)),
        "b_temp" to EntityState("b_temp", StateValue.Numeric(19.8)),
        "ba_light" to EntityState("ba_light", StateValue.Light(true)),
        "ba_temp" to EntityState("ba_temp", StateValue.Numeric(23.0)),
        "ba_hum" to EntityState("ba_hum", StateValue.Numeric(71.0)),
        "g_door" to EntityState("g_door", StateValue.Cover(CoverState.CLOSED, null)),
        "ga_light" to EntityState("ga_light", StateValue.Light(false), available = false),
        "ga_temp" to EntityState("ga_temp", StateValue.Numeric(12.4)),
    )

    fun house(selected: RoomFilter): HomeUiState = HomeUiBuilder.build(
        house.entities, states, areas, selected, LinkState.Connected(TransportMode.DEMO), Locale.US, hubGroups = house.hubGroups,
    )
}
