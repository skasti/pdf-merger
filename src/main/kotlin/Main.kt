package org.skasti

import com.github.ajalt.mordant.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.any { it == "--help" || it == "-h" }) {
        println("Usage: pdf-merger [starting-directory]")
        println("Select 1–15 PDFs with Space. Tab switches panels; +/− changes the order.")
        println("G: open directory, O: choose output file, M: merge, Q: quit.")
        return
    }
    if (args.size > 1) {
        System.err.println("Usage: pdf-merger [starting-directory]")
        exitProcess(1)
    }

    try {
        val startDirectory = Path.of(args.firstOrNull() ?: ".").toRealPath()
        TerminalBuilder.builder().system(true).provider("jni").dumb(false).build().use { keyboard ->
            val terminal = Terminal(interactive = true, terminalInterface = JLineTerminalInterface(keyboard))
            PdfMergerTui(terminal, keyboard, startDirectory).run()
        }
    } catch (exception: Exception) {
        System.err.println("Could not start PDF Merger: ${exception.message}")
        System.err.println("In IntelliJ: select the \"PDF-merger\" Run configuration to open Windows Terminal.")
        System.err.println("You can also run build/install/pdf-merger/bin/pdf-merger directly in a terminal.")
        exitProcess(1)
    }
}
