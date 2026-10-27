package com.marketbrief

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApplicationTest {

    @Test
    fun `port defaults to 8080 when unset`() {
        assertEquals(8080, resolvePort(null))
    }

    @Test
    fun `valid port is accepted`() {
        assertEquals(9090, resolvePort("9090"))
    }

    @Test
    fun `unparseable port fails fast`() {
        assertFailsWith<IllegalArgumentException> { resolvePort("eighty") }
    }

    @Test
    fun `out of range port fails fast`() {
        assertFailsWith<IllegalArgumentException> { resolvePort("70000") }
    }
}
