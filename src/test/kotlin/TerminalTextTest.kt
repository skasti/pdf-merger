package org.skasti

import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalTextTest {
    private val text = TerminalText(Terminal(interactive = false))

    @Test
    fun `wide characters use two cells and are never split`() {
        assertEquals("A界", text.fit("A界B", 3))
        assertEquals("A ", text.fit("A界B", 2))
        assertEquals("界B", text.tail("A界B", 3))
        assertEquals("B", text.tail("A界B", 2))
    }

    @Test
    fun `combining marks stay with their character`() {
        assertEquals("e\u0301", text.fit("e\u0301x", 1))
        assertEquals("e\u0301", text.tail("xe\u0301", 1))
    }

    @Test
    fun `supplementary characters are not split`() {
        assertEquals("😀", text.fit("😀x", 2))
        assertEquals("😀", text.tail("x😀", 2))
        assertEquals(" ", text.fit("😀", 1))
    }

    @Test
    fun `filenames cannot inject terminal controls or extra lines`() {
        assertEquals("a b c d", text.fit("a\u001bb\nc\u2028d", 7))
        assertEquals("", text.fit("abc", 0))
        assertEquals("   ", text.fit("", 3))
    }
}
