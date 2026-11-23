package com.gekkotron.nidoo.flows

import kotlin.test.Test
import kotlin.test.assertEquals

class FlowIdEncodingTest {
    @Test
    fun encodesLikeUrlEncoder() {
        assertEquals("a%20b%2Bc%2F%C3%A9*_.-%7E", encodePathPart("a b+c/é*_.-~"))
    }
}
