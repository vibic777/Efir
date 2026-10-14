package ru.efir.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AccessKeyCursorTest {
    @Test
    fun cursorStandsAfterTheTypedCharacter() {
        // Набрали пятый символ: ключ стал «ABCD-E», курсор — после «E», а не перед ним.
        assertEquals(6, accessKeyCursor("ABCD-E", 5))
        assertEquals(4, accessKeyCursor("ABCD", 4))
        assertEquals(1, accessKeyCursor("A", 1))
    }

    @Test
    fun cursorInTheMiddleKeepsItsPlace() {
        assertEquals(2, accessKeyCursor("ABCD-EFGH", 2))
        assertEquals(0, accessKeyCursor("ABCD-EFGH", 0))
        assertEquals(9, accessKeyCursor("ABCD-EFGH", 20))
    }
}
