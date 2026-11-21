package com.gekkotron.nidoo.feature.home.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomIconTest {
    @Test
    fun `a known Area icon key wins over the name`() = assertEquals(RoomIcon.GARAGE, RoomIcon.of("garage", "Salon"))

    @Test
    fun `an unknown Area icon key falls back to the name`() = assertEquals(RoomIcon.LIVING_ROOM, RoomIcon.of("mdi:sofa", "Salon"))

    @Test
    fun `names match in English and French, whatever the case and accents`() {
        mapOf(
            "Living room" to RoomIcon.LIVING_ROOM, "SALON" to RoomIcon.LIVING_ROOM, "Cuisine" to RoomIcon.KITCHEN, "Kitchen" to RoomIcon.KITCHEN,
            "Chambre parents" to RoomIcon.BEDROOM, "Kids bedroom" to RoomIcon.BEDROOM, "Salle de bain" to RoomIcon.BATHROOM, "Bathroom" to RoomIcon.BATHROOM,
            "Bureau" to RoomIcon.OFFICE, "Office" to RoomIcon.OFFICE, "Garage" to RoomIcon.GARAGE, "Jardin" to RoomIcon.GARDEN, "Garden" to RoomIcon.GARDEN,
            "Entrée" to RoomIcon.ENTRANCE, "Entrance" to RoomIcon.ENTRANCE, "Buanderie" to RoomIcon.LAUNDRY, "Laundry" to RoomIcon.LAUNDRY,
            "WC" to RoomIcon.TOILET, "Toilettes" to RoomIcon.TOILET,
        ).forEach { (name, icon) -> assertEquals(name, icon, RoomIcon.of(null, name)) }
    }

    @Test
    fun `anything else is generic`() {
        assertEquals(RoomIcon.GENERIC, RoomIcon.of(null, "Mezzanine"))
        assertEquals(RoomIcon.GENERIC, RoomIcon.of(null, null))
    }
}
