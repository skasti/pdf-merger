package org.skasti

import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text
import java.text.BreakIterator

/** Clip by terminal cells without splitting a Unicode character or accepting terminal controls. */
internal class TerminalText(private val terminal: Terminal) {
    fun fit(value: String, width: Int): String {
        val available = width.coerceAtLeast(0)
        val clipped = clip(clean(value), available, fromEnd = false)
        return clipped + " ".repeat((available - cells(clipped)).coerceAtLeast(0))
    }

    fun tail(value: String, width: Int): String =
        clip(clean(value), width.coerceAtLeast(1), fromEnd = true)

    private fun cells(value: String) = Text(value).render(terminal, Int.MAX_VALUE).width

    private fun clip(value: String, width: Int, fromEnd: Boolean): String {
        if (cells(value) <= width) return value
        val iterator = BreakIterator.getCharacterInstance().apply { setText(value) }
        if (fromEnd) iterator.last() else iterator.first()
        var result = ""
        while (true) {
            val next = if (fromEnd) iterator.previous() else iterator.next()
            if (next == BreakIterator.DONE) break
            val candidate = if (fromEnd) value.substring(next) else value.substring(0, next)
            if (cells(candidate) > width) break
            result = candidate
        }
        return result
    }

    private fun clean(value: String) = value.map {
        if (it.isISOControl() || it == '\u2028' || it == '\u2029') ' ' else it
    }.joinToString("")
}
