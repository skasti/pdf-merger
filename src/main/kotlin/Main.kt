package org.skasti

import com.github.ajalt.mordant.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.any { it == "--help" || it == "-h" }) {
        println("Bruk: pdf-merger [startmappe]")
        println("Velg 1–15 PDF-filer med mellomrom. Tab bytter panel, +/− endrer rekkefølge.")
        println("G: åpne mappe, O: velg utfil, M: slå sammen, Q: avslutt.")
        return
    }
    if (args.size > 1) {
        System.err.println("Bruk: pdf-merger [startmappe]")
        exitProcess(1)
    }

    try {
        val startDirectory = Path.of(args.firstOrNull() ?: ".").toRealPath()
        TerminalBuilder.builder().system(true).provider("jni").dumb(false).build().use { keyboard ->
            val terminal = Terminal(interactive = true, terminalInterface = JLineTerminalInterface(keyboard))
            PdfMergerTui(terminal, keyboard, startDirectory).run()
        }
    } catch (exception: Exception) {
        System.err.println("Kunne ikke starte PDF-sammenslåing: ${exception.message}")
        System.err.println("I IntelliJ: velg Run-konfigurasjonen \"PDF-merger\", som åpner Windows Terminal.")
        System.err.println("Du kan også kjøre build/install/pdf-merger/bin/pdf-merger direkte i en terminal.")
        exitProcess(1)
    }
}
