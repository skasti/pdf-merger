package org.skasti

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.text.PDFTextStripper
import org.jline.terminal.Size
import org.jline.terminal.impl.DumbTerminal
import org.jline.terminal.spi.TerminalProvider
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfMergerTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `merges every page in the requested order and preserves dimensions and sources`() {
        val first = createPdf("first.pdf", "First-1", "First-2")
        val second = createPdf("second.PDF", "Second-1")
        val originals = listOf(first, second).map(Files::readAllBytes)
        val output = directory.resolve("merged.pdf")

        val result = PdfMerger().merge(listOf(second, first), output)

        assertEquals(MergeResult(output, 2, 3), result)
        Loader.loadPDF(output.toFile()).use { merged ->
            assertEquals(3, merged.numberOfPages)
            assertEquals(listOf("Second-1", "First-1", "First-2"), pageTexts(merged))
            assertEquals(PDRectangle.A4.width, merged.getPage(0).mediaBox.width)
            assertEquals(PDRectangle.A4.height, merged.getPage(0).mediaBox.height)
        }
        listOf(first, second).forEachIndexed { index, path -> assertContentEquals(originals[index], Files.readAllBytes(path)) }
        assertNoTemporaryFiles()
    }

    @Test
    fun `allows both one file and the maximum of fifteen files`() {
        val inputs = (1..15).map { createPdf("$it.pdf", "Page-$it") }
        assertEquals(1, PdfMerger().merge(inputs.take(1), directory.resolve("one.pdf")).pages)
        val output = directory.resolve("fifteen.pdf")
        assertEquals(15, PdfMerger().merge(inputs, output).pages)
        Loader.loadPDF(output.toFile()).use { assertEquals((1..15).map { "Page-$it" }, pageTexts(it)) }
    }

    @Test
    fun `rejects empty selections and more than fifteen inputs without writing output`() {
        val source = createPdf("source.pdf", "Page")
        val output = directory.resolve("merged.pdf")
        assertFailsWith<IllegalArgumentException> { PdfMerger().merge(emptyList(), output) }
        assertFailsWith<IllegalArgumentException> { PdfMerger().merge(List(16) { source }, output) }
        assertFalse(Files.exists(output))
    }

    @Test
    fun `refuses to overwrite either a source or an existing destination`() {
        val source = createPdf("source.pdf", "Keep source")
        val existing = createPdf("existing.pdf", "Keep destination")
        val sourceBytes = Files.readAllBytes(source)
        val outputBytes = Files.readAllBytes(existing)
        assertFailsWith<IllegalArgumentException> { PdfMerger().merge(listOf(source), source) }
        assertFailsWith<IllegalArgumentException> { PdfMerger().merge(listOf(source), existing) }
        assertContentEquals(sourceBytes, Files.readAllBytes(source))
        assertContentEquals(outputBytes, Files.readAllBytes(existing))
        assertNoTemporaryFiles()
    }

    @Test
    fun `rejects corrupt and missing PDFs without leaving a partial result`() {
        val valid = createPdf("valid.pdf", "Valid")
        val corrupt = Files.writeString(directory.resolve("broken.pdf"), "This is not a PDF")
        val output = directory.resolve("merged.pdf")
        val failure = assertFailsWith<IOException> { PdfMerger().merge(listOf(valid, corrupt), output) }
        assertTrue(failure.message.orEmpty().contains("broken.pdf"))
        assertFailsWith<IllegalArgumentException> {
            PdfMerger().merge(listOf(valid, directory.resolve("missing.pdf")), output)
        }
        assertFalse(Files.exists(output))
        assertNoTemporaryFiles()
    }

    @Test
    fun `explains password protection without creating output`() {
        val encrypted = directory.resolve("locked.pdf")
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.protect(StandardProtectionPolicy("owner", "secret", AccessPermission()))
            document.save(encrypted.toFile())
        }
        val output = directory.resolve("merged.pdf")
        val failure = assertFailsWith<IllegalArgumentException> { PdfMerger().merge(listOf(encrypted), output) }
        assertTrue(failure.message.orEmpty().contains("passordbeskyttet"))
        assertFalse(Files.exists(output))
        assertNoTemporaryFiles()
    }

    @Test
    fun `rejects invalid destination extension and missing parent directory`() {
        val source = createPdf("source.pdf", "Source")
        assertFailsWith<IllegalArgumentException> { PdfMerger().merge(listOf(source), directory.resolve("result.txt")) }
        assertFailsWith<IllegalArgumentException> { PdfMerger().merge(listOf(source), directory.resolve("missing/result.pdf")) }
        assertNoTemporaryFiles()
    }

    @Test
    fun `selection enforces the limit and still permits removing files at the limit`() {
        val files = (1..16).map { Files.createFile(directory.resolve("$it.pdf")) }
        val selection = PdfSelection()
        files.take(15).forEach(selection::toggle)
        assertFailsWith<IllegalArgumentException> { selection.toggle(files.last()) }
        assertEquals(15, selection.paths.size)
        selection.toggle(files.first())
        selection.toggle(files.last())
        assertEquals(files.drop(1), selection.paths)
    }

    @Test
    fun `selection reorders and removes files without moving beyond the ends`() {
        val first = Files.createFile(directory.resolve("first.pdf"))
        val second = Files.createFile(directory.resolve("second.pdf"))
        val selection = PdfSelection()
        selection.toggle(first)
        selection.toggle(second)
        assertEquals(0, selection.move(0, -1))
        assertEquals(1, selection.move(0, 1))
        assertEquals(listOf(second, first), selection.paths)
        assertEquals(1, selection.move(1, 1))
        selection.toggle(directory.resolve(".").resolve("first.pdf"))
        assertEquals(listOf(second), selection.paths)
        selection.remove(0)
        assertTrue(selection.paths.isEmpty())
    }

    @Test
    fun `native terminal provider is discoverable on the application classpath`() {
        assertEquals("jni", TerminalProvider.load("jni").name())
    }

    @Test
    fun `unsupported terminals fail before waiting for input`() {
        DumbTerminal("test", "dumb", ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), Charsets.UTF_8).use { terminal ->
            val failure = assertFailsWith<IllegalArgumentException> { PdfMergerTui(terminal, directory).run() }
            assertTrue(failure.message.orEmpty().contains("TERM=dumb"))
        }
    }

    @Test
    fun `terminal workflow selects reorders names and merges files using the keyboard`() {
        createPdf("a.pdf", "First")
        createPdf("b.pdf", "Second")
        // Skip the parent folder, select both PDFs, move the second one up, set a name, merge.
        val input = "\u001b[B \u001b[B \t\u001b[B+o\u0015ferdig æøå\rmq"
        val screen = runTerminal(input)
        val output = directory.resolve("ferdig æøå.pdf")
        assertTrue(Files.exists(output), screen)
        Loader.loadPDF(output.toFile()).use { assertEquals(listOf("Second", "First"), pageTexts(it)) }
        assertTrue(screen.contains("Lagret 2 sider fra 2 filer"))
    }

    @Test
    fun `terminal keeps running after a failed merge and permits cancelling a path prompt`() {
        val screen = runTerminal("mo\u001bq")
        assertTrue(screen.contains("Velg minst én PDF-fil først."))
        assertFalse(Files.exists(directory.resolve("samlet.pdf")))
    }

    private fun runTerminal(input: String): String {
        val output = ByteArrayOutputStream()
        DumbTerminal("test", "xterm", ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)), output, Charsets.UTF_8).use { terminal ->
            terminal.size = Size(100, 30)
            PdfMergerTui(terminal, directory).run()
        }
        return output.toString(Charsets.UTF_8)
    }

    private fun createPdf(name: String, vararg pages: String): Path {
        val path = directory.resolve(name)
        PDDocument().use { document ->
            pages.forEach { text ->
                val page = PDPage(PDRectangle.A4)
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                    content.newLineAtOffset(40f, 750f)
                    content.showText(text)
                    content.endText()
                }
            }
            document.save(path.toFile())
        }
        return path
    }

    private fun pageTexts(document: PDDocument) = (1..document.numberOfPages).map { page ->
        PDFTextStripper().apply { startPage = page; endPage = page }.getText(document).trim()
    }

    private fun assertNoTemporaryFiles() {
        Files.list(directory).use { files -> assertFalse(files.anyMatch { it.fileName.toString().startsWith(".pdf-merger-") }) }
    }
}
