package com.gekkotron.nidoo.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AreaTest {
    @Test
    fun `group names round-trip and an old document decodes with none`() {
        val doc = AreasDocument(listOf(Area("living", "Living room", groupNames = mapOf("cover" to "Volets baie"))))
        val json = NidooJson.encodeToString(AreasDocument.serializer(), doc)
        assertEquals(doc, NidooJson.decodeFromString(AreasDocument.serializer(), json))
        val old = NidooJson.decodeFromString(AreasDocument.serializer(), """{"areas":[{"id":"living","name":"Living room"}]}""")
        assertEquals(emptyMap(), old.areas.single().groupNames)
    }
}
