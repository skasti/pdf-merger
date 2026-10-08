package org.skasti

import com.github.ajalt.mordant.terminal.Terminal
import org.jline.terminal.impl.DumbTerminal
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class JLineTerminalInterfaceTest {
    @Test
    fun `Unicode output goes through the native terminal writer instead of stdout`() {
        val output = ByteArrayOutputStream()
        DumbTerminal("test", "xterm", ByteArrayInputStream(byteArrayOf()), output, Charsets.UTF_16LE).use { native ->
            val terminal = Terminal(interactive = true, terminalInterface = JLineTerminalInterface(native))
            terminal.print("æøå ÆØÅ ↑/↓ 界 😀")
        }
        assertEquals("æøå ÆØÅ ↑/↓ 界 😀", output.toString(Charsets.UTF_16LE))
    }

    @Test
    fun `screen dimensions follow the native terminal window`() {
        DumbTerminal("test", "xterm", ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), Charsets.UTF_8).use { native ->
            native.size = org.jline.terminal.Size(120, 35)
            val terminal = Terminal(interactive = true, terminalInterface = JLineTerminalInterface(native))
            assertEquals(120, terminal.size.width)
            assertEquals(35, terminal.size.height)
            native.size = org.jline.terminal.Size(64, 16)
            val updated = terminal.updateSize()
            assertEquals(64, updated.width)
            assertEquals(16, updated.height)
        }
    }
}
