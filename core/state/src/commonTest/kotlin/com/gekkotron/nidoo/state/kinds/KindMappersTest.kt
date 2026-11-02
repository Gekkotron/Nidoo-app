package com.gekkotron.nidoo.state.kinds

import com.gekkotron.nidoo.model.Kind
import kotlin.test.Test
import kotlin.test.assertEquals

class KindMappersTest {
    @Test
    fun `every kind has a dedicated mapper`() {
        val mappers = Kind.entries.map { KindMappers.forKind(it) }
        assertEquals(Kind.entries.size, mappers.toSet().size)
    }
}
