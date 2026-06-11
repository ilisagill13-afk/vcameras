package com.usvisa.appointment

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun dateParsingIsCorrect() {
        val date = "2025-06-15"
        assertEquals("2025", date.substringBefore("-"))
    }
}
