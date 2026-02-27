package io.github.tritbool.euc_soh.desktop

import io.github.tritbool.euc_soh.core.CsvSource
import java.io.File
import java.io.InputStream

/**
 * Desktop (JVM) implementation of CsvSource.
 * Uses standard java.io.File for file operations.
 */
class DesktopCsvOpener : CsvSource {

    override fun openCsvStream(path: String): InputStream {
        return File(path).inputStream()
    }

    override fun listCsvFiles(folderPath: String): List<String> {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            return emptyList()
        }

        return folder.listFiles { file ->
            file.isFile && file.extension.equals("csv", ignoreCase = true)
        }?.map { it.absolutePath } ?: emptyList()
    }
}
