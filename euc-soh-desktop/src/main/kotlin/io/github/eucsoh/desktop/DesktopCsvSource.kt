package io.github.eucsoh.desktop

import io.github.eucsoh.CsvSource
import java.io.File
import java.io.InputStream

/**
 * Desktop implementation of CsvSource using File API.
 */
class DesktopCsvSource : CsvSource {

    override fun openCsvStream(path: String): InputStream {
        return File(path).inputStream()
    }

    override fun listCsvFiles(folderPath: String): List<String> {
        return File(folderPath)
            .listFiles { f -> f.extension == "csv" }
            ?.map { it.absolutePath }
            ?: emptyList()
    }
}
