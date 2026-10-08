package org.skasti

import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors.brightBlue
import com.github.ajalt.mordant.rendering.TextColors.brightCyan
import com.github.ajalt.mordant.rendering.TextColors.brightGreen
import com.github.ajalt.mordant.rendering.TextColors.brightRed
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.TextStyles.inverse
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Panel
import org.jline.keymap.BindingReader
import org.jline.keymap.KeyMap
import org.jline.utils.InfoCmp.Capability
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max
import org.jline.terminal.Terminal as KeyboardTerminal

private enum class Key {
    UP, DOWN, TAB, ENTER, SPACE, BACK, DELETE, GO, OUTPUT, MERGE, QUIT, ESCAPE, EARLIER, LATER, TEXT
}

private data class Entry(val path: Path, val directory: Boolean, val parent: Boolean = false)

class PdfMergerTui(
    private val terminal: Terminal,
    private val keyboard: KeyboardTerminal,
    startDirectory: Path,
) {
    private val reader = BindingReader(keyboard.reader())
    private val text = TerminalText(terminal)
    private val selection = PdfSelection()
    private var directory = startDirectory
    private var entries = emptyList<Entry>()
    private var browserIndex = 0
    private var selectedIndex = 0
    private var selectionFocused = false
    private var output = startDirectory.resolve("samlet.pdf")
    private var message = "Velg PDF-filer i ønsket rekkefølge. Du kan hente filer fra flere mapper."
    private var failed = false
    private val keys = keyMap()

    fun run() {
        require(!keyboard.type.startsWith("dumb")) {
            "Terminalen støtter ikke et interaktivt grensesnitt (TERM=${keyboard.type})."
        }
        openDirectory(directory)
        val originalAttributes = keyboard.enterRawMode()
        try {
            terminal.rawPrint("\u001b[?1049h\u001b[?25l")
            keyboard.puts(Capability.keypad_xmit)
            keyboard.flush()
            while (true) {
                render()
                val key = reader.readBinding(keys) ?: break
                if (key == Key.QUIT || key == Key.ESCAPE) break
                try {
                    handle(key)
                } catch (exception: Exception) {
                    setMessage(exception.message ?: "Operasjonen mislyktes.", error = true)
                }
            }
        } finally {
            try {
                terminal.rawPrint("\u001b[?25h\u001b[?1049l")
            } finally {
                try {
                    keyboard.attributes = originalAttributes
                } finally {
                    keyboard.puts(Capability.keypad_local)
                    keyboard.flush()
                }
            }
        }
    }

    private fun handle(key: Key) {
        when (key) {
            Key.UP -> if (selectionFocused) selectedIndex-- else browserIndex--
            Key.DOWN -> if (selectionFocused) selectedIndex++ else browserIndex++
            Key.TAB -> selectionFocused = !selectionFocused
            Key.ENTER, Key.SPACE -> {
                if (selectionFocused) {
                    selection.remove(selectedIndex)
                } else {
                    entries.getOrNull(browserIndex)?.let { entry ->
                        if (entry.directory) {
                            if (key == Key.ENTER) openDirectory(entry.path)
                        } else {
                            selection.toggle(entry.path)
                            setMessage("${selection.paths.size} av $MAX_PDF_FILES filer valgt.")
                        }
                    }
                }
            }
            Key.DELETE -> if (selectionFocused) selection.remove(selectedIndex)
            Key.BACK -> if (selectionFocused) selection.remove(selectedIndex) else directory.parent?.let(::openDirectory)
            Key.EARLIER -> if (selectionFocused) selectedIndex = selection.move(selectedIndex, -1)
            Key.LATER -> if (selectionFocused) selectedIndex = selection.move(selectedIndex, 1)
            Key.GO -> prompt("Åpne mappe", directory.toString())?.let { openDirectory(resolvePath(it)) }
            Key.OUTPUT -> prompt("Lagre som (ny fil)", output.toString())?.let { value ->
                val path = resolvePath(value)
                output = if (path.fileName.toString().endsWith(".pdf", ignoreCase = true)) path
                else path.resolveSibling("${path.fileName}.pdf")
                setMessage("Utfil valgt. Trykk M for å slå sammen.")
            }
            Key.MERGE -> {
                require(selection.paths.isNotEmpty()) { "Velg minst én PDF-fil først." }
                setMessage("Slår sammen ${selection.paths.size} filer. Vent …")
                render()
                val result = PdfMerger().merge(selection.paths, output)
                setMessage("Lagret ${result.pages} sider fra ${result.files} filer: ${result.output}")
            }
            else -> Unit
        }
        browserIndex = browserIndex.coerceIn(0, max(0, entries.lastIndex))
        selectedIndex = selectedIndex.coerceIn(0, max(0, selection.paths.lastIndex))
    }

    private fun openDirectory(path: Path) {
        val resolved = path.toRealPath()
        require(Files.isDirectory(resolved)) { "Dette er ikke en mappe: $path" }
        val contents = Files.list(resolved).use { stream ->
            stream.map { Entry(it, Files.isDirectory(it)) }
                .filter { it.directory || (Files.isRegularFile(it.path) && it.path.fileName.toString().endsWith(".pdf", true)) }
                .toList()
                .sortedWith(compareBy<Entry> { !it.directory }.thenBy { it.path.fileName.toString().lowercase() })
        }
        entries = listOfNotNull(resolved.parent?.let { Entry(it, directory = true, parent = true) }) + contents
        directory = resolved
        browserIndex = 0
        setMessage("Enter åpner mapper. Mellomrom velger eller fjerner en PDF.")
    }

    private fun resolvePath(value: String): Path {
        val cleaned = value.trim().removeSurrounding("\"")
        require(cleaned.isNotBlank()) { "Skriv inn en filsti." }
        val expanded = when {
            cleaned == "~" -> Path.of(System.getProperty("user.home"))
            cleaned.startsWith("~/") || cleaned.startsWith("~\\") ->
                Path.of(System.getProperty("user.home")).resolve(cleaned.substring(2))
            else -> Path.of(cleaned)
        }
        return directory.resolve(expanded).toAbsolutePath().normalize()
    }

    private fun setMessage(text: String, error: Boolean = false) {
        message = text
        failed = error
    }

    private fun render(promptTitle: String? = null, input: String = "") {
        val size = terminal.updateSize()
        // Leave the last cell and row unused so console wrapping cannot scroll the frame.
        val rows = max(1, size.height - 1)
        val columns = max(1, size.width - 1)
        val lines = mutableListOf<String>()
        fun line(value: String, style: (String) -> String = { it }) {
            lines.add(style(fit(value, columns)))
        }
        if (size.width < 64 || size.height < 16) {
            line("Gjør terminalen større (minst 64 × 16). Q avslutter.")
        } else {
            val listRows = rows - 12
            val leftWidth = (columns - 1) / 2
            val rightWidth = columns - leftWidth - 1
            val chosen = selection.paths
            val browserStart = windowStart(browserIndex, entries.size, listRows)
            val selectionStart = windowStart(selectedIndex, chosen.size, listRows)
            line("PDF-SAMMENSLÅING    ${chosen.size}/$MAX_PDF_FILES filer") { (brightCyan + bold)(it) }
            line("Mappe: $directory")
            line("Utfil: $output")
            val browserLines = mutableListOf<String>()
            val selectionLines = mutableListOf<String>()
            repeat(listRows) { row ->
                val leftIndex = browserStart + row
                val rightIndex = selectionStart + row
                val left = entries.getOrNull(leftIndex)?.let { entry ->
                    val cursor = if (!selectionFocused && leftIndex == browserIndex) ">" else " "
                    val mark = if (entry.directory) "[mappe]" else if (entry.path in chosen) "[x]" else "[ ]"
                    "$cursor $mark ${if (entry.parent) ".." else entry.path.fileName}"
                } ?: if (entries.isEmpty() && row == 0) "Ingen PDF-filer i mappen." else ""
                val right = chosen.getOrNull(rightIndex)?.let { path ->
                    val cursor = if (selectionFocused && rightIndex == selectedIndex) ">" else " "
                    "$cursor ${rightIndex + 1}. ${path.fileName}"
                } ?: if (chosen.isEmpty() && row == 0) "Ingen filer valgt." else ""
                val leftText = fit(left, leftWidth - 4)
                val rightText = fit(right, rightWidth - 4)
                browserLines += if (!selectionFocused && leftIndex == browserIndex && leftIndex < entries.size)
                    (brightCyan + bold + inverse)(leftText) else leftText
                selectionLines += if (selectionFocused && rightIndex == selectedIndex && rightIndex < chosen.size)
                    (brightCyan + bold + inverse)(rightText) else rightText
            }
            val browser = panel("FILER", browserLines, leftWidth, focused = !selectionFocused)
            val ordering = panel("REKKEFØLGE", selectionLines, rightWidth, focused = selectionFocused)
            browser.indices.forEach { row -> lines += browser[row] + " " + ordering[row] }
            val activePath = if (selectionFocused) chosen.getOrNull(selectedIndex)
            else entries.getOrNull(browserIndex)?.path
            line("Markert: ${activePath ?: "–"}")
            line(message) { if (failed) brightRed(it) else brightGreen(it) }
            line("↑/↓ Flytt markør  Tab Bytt panel  Enter Åpne/velg")
            line("Mellomrom Velg/fjern  + Opp i rekkefølgen  - Ned  Del Fjern")
            line("G Åpne mappe  O Velg utfil  M Slå sammen  Q Avslutt")
            line(if (promptTitle != null) "$promptTitle: ${inputTail(input, columns - promptTitle.length - 4)}▏" else "")
            line(if (promptTitle != null) "Enter Bekreft  Esc Avbryt  Ctrl+U Tøm feltet" else "") { cyan(it) }
        }
        // A fullscreen UI has a fixed origin. Relative textAnimation updates in Mordant 3.1.0
        // skip moving up on terminals reporting ANSI cursor support (including Windows Terminal).
        terminal.cursor.move {
            setPosition(0, 0)
            clearScreenAfterCursor()
        }
        terminal.print(lines.take(rows).joinToString("\n"))
    }

    private fun panel(title: String, lines: List<String>, width: Int, focused: Boolean): List<String> {
        val widget = Panel(
            content = lines.joinToString("\n"),
            title = if (focused) "> $title" else title,
            expand = true,
            padding = Padding(top = 0, right = 1, bottom = 0, left = 1),
            titleAlign = TextAlign.LEFT,
            borderStyle = if (focused) brightCyan else brightBlue,
        )
        return terminal.render(widget.render(terminal, width)).split("\n")
    }

    private fun prompt(title: String, initial: String): String? {
        val input = StringBuilder(initial)
        val promptKeys = KeyMap<Key>().apply {
            unicode = Key.TEXT
            nomatch = Key.TEXT
            bind(Key.ENTER, "\r", "\n")
            bind(Key.ESCAPE, "\u001b", "\u0003")
            bind(Key.BACK, "\u007f", "\b")
            bind(Key.DELETE, "\u0015")
            // Consume navigation keys so escape sequences cannot enter a path.
            bind(Key.UP, "\u001b[A", "\u001bOA")
            bind(Key.DOWN, "\u001b[B", "\u001bOB")
            bind(Key.TAB, "\u001b[C", "\u001b[D", "\u001bOC", "\u001bOD", "\t", "\u001b[3~")
            setAmbiguousTimeout(150)
        }
        while (true) {
            render(title, input.toString())
            when (reader.readBinding(promptKeys) ?: return null) {
                Key.ENTER -> return input.toString()
                Key.ESCAPE -> return null
                Key.BACK -> if (input.isNotEmpty()) input.delete(input.offsetByCodePoints(input.length, -1), input.length)
                Key.DELETE -> input.setLength(0)
                Key.TEXT -> reader.lastBinding.codePoints().filter { !Character.isISOControl(it) }.forEach { input.appendCodePoint(it) }
                else -> Unit
            }
        }
    }

    private fun keyMap() = KeyMap<Key>().apply {
        bind(Key.UP, "\u001b[A", "\u001bOA")
        bind(Key.DOWN, "\u001b[B", "\u001bOB")
        KeyMap.key(keyboard, Capability.key_up)?.let { bind(Key.UP, it) }
        KeyMap.key(keyboard, Capability.key_down)?.let { bind(Key.DOWN, it) }
        bind(Key.TAB, "\t")
        bind(Key.ENTER, "\r", "\n")
        bind(Key.SPACE, " ")
        bind(Key.BACK, "\u007f", "\b")
        bind(Key.DELETE, "\u001b[3~")
        bind(Key.GO, "g", "G")
        bind(Key.OUTPUT, "o", "O")
        bind(Key.MERGE, "m", "M")
        bind(Key.QUIT, "q", "Q", "\u0003", "\u0004")
        bind(Key.ESCAPE, "\u001b")
        bind(Key.EARLIER, "+")
        bind(Key.LATER, "-")
        setAmbiguousTimeout(150)
    }

    private fun windowStart(index: Int, size: Int, rows: Int) =
        (index - rows / 2).coerceIn(0, max(0, size - rows))

    private fun fit(value: String, width: Int) = text.fit(value, width)

    private fun inputTail(value: String, width: Int) = text.tail(value, width)
}
