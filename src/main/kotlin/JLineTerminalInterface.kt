package org.skasti

import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.PrintRequest
import com.github.ajalt.mordant.terminal.StandardTerminalInterface
import org.jline.terminal.Terminal

/** Keep Mordant output on the native console used for keyboard input and window sizing. */
internal class JLineTerminalInterface(private val nativeTerminal: Terminal) : StandardTerminalInterface() {
    override fun getTerminalSize(): Size = nativeTerminal.size.let { Size(it.columns, it.rows) }

    override fun completePrintRequest(request: PrintRequest) {
        // On Windows this writer uses WriteConsoleW, avoiding UTF-8 bytes interpreted in an OEM code page.
        nativeTerminal.writer().apply {
            print(request.text)
            if (request.trailingLinebreak) println()
            flush()
        }
    }
}
