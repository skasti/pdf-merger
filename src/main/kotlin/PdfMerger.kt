package org.skasti

import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

const val MAX_PDF_FILES = 15

data class MergeResult(val output: Path, val files: Int, val pages: Int)

class PdfSelection {
    private val selected = mutableListOf<Path>()
    val paths: List<Path> get() = selected.toList()

    fun toggle(path: Path) {
        val existing = selected.indexOfFirst { it == path || Files.isSameFile(it, path) }
        if (existing >= 0) {
            selected.removeAt(existing)
            return
        }
        require(selected.size < MAX_PDF_FILES) { "You can select up to $MAX_PDF_FILES PDF files." }
        require(Files.isRegularFile(path) && path.fileName.toString().endsWith(".pdf", ignoreCase = true)) {
            "Select a PDF file."
        }
        selected.add(path.toRealPath())
    }

    fun remove(index: Int) {
        if (index in selected.indices) selected.removeAt(index)
    }

    fun move(index: Int, offset: Int): Int {
        val target = index + offset
        if (index !in selected.indices || target !in selected.indices) return index
        selected.add(target, selected.removeAt(index))
        return target
    }
}

class PdfMerger {
    fun merge(sources: List<Path>, destination: Path): MergeResult {
        require(sources.size in 1..MAX_PDF_FILES) { "Select between 1 and $MAX_PDF_FILES PDF files." }
        val output = destination.toAbsolutePath().normalize()
        require(output.fileName.toString().endsWith(".pdf", ignoreCase = true)) { "The output filename must end in .pdf." }
        require(Files.isDirectory(output.parent)) { "The output directory does not exist: ${output.parent}" }
        require(!Files.exists(output, NOFOLLOW_LINKS)) { "The output file already exists. Choose a different filename." }

        var pages = 0
        val validatedSources = sources.map { source ->
            require(Files.isRegularFile(source) && Files.isReadable(source)) { "Cannot read file: $source" }
            try {
                Loader.loadPDF(source.toFile()).use { document ->
                    require(!document.isEncrypted) { "${source.fileName} is encrypted. Use an unencrypted copy." }
                    pages += document.numberOfPages
                }
            } catch (_: InvalidPasswordException) {
                throw IllegalArgumentException("${source.fileName} requires a password. Use an unencrypted copy.")
            } catch (exception: IOException) {
                throw IOException("Could not read PDF file ${source.fileName}: ${exception.message}", exception)
            }
            source.toFile()
        }

        val temporary = Files.createTempFile(output.parent, ".pdf-merger-", ".tmp")
        try {
            val merger = PDFMergerUtility()
            validatedSources.forEach { merger.addSource(it) }
            merger.destinationFileName = temporary.toString()
            merger.mergeDocuments(IOUtils.createTempFileOnlyStreamCache())
            // Publish only a finished PDF; never replace an existing file.
            Files.move(temporary, output)
        } finally {
            Files.deleteIfExists(temporary)
        }
        return MergeResult(output, sources.size, pages)
    }
}
