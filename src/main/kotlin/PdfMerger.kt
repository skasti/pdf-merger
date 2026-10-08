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
        require(selected.size < MAX_PDF_FILES) { "Du kan velge maksimalt $MAX_PDF_FILES PDF-filer." }
        require(Files.isRegularFile(path) && path.fileName.toString().endsWith(".pdf", ignoreCase = true)) {
            "Velg en PDF-fil."
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
        require(sources.size in 1..MAX_PDF_FILES) { "Velg mellom 1 og $MAX_PDF_FILES PDF-filer." }
        val output = destination.toAbsolutePath().normalize()
        require(output.fileName.toString().endsWith(".pdf", ignoreCase = true)) { "Utfilen må slutte på .pdf." }
        require(Files.isDirectory(output.parent)) { "Mappen for utfilen finnes ikke: ${output.parent}" }
        require(!Files.exists(output, NOFOLLOW_LINKS)) { "Utfilen finnes allerede. Velg et annet filnavn." }

        var pages = 0
        val validatedSources = sources.map { source ->
            require(Files.isRegularFile(source) && Files.isReadable(source)) { "Kan ikke lese filen: $source" }
            try {
                Loader.loadPDF(source.toFile()).use { document ->
                    require(!document.isEncrypted) { "${source.fileName} er kryptert. Bruk en ukryptert kopi." }
                    pages += document.numberOfPages
                }
            } catch (_: InvalidPasswordException) {
                throw IllegalArgumentException("${source.fileName} er passordbeskyttet. Bruk en ukryptert kopi.")
            } catch (exception: IOException) {
                throw IOException("Kunne ikke lese PDF-filen ${source.fileName}: ${exception.message}", exception)
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
