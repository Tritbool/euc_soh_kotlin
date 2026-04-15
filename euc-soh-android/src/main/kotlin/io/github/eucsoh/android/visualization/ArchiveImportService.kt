/*
 * EUC SoH Kotlin - State of Health analysis for Electric Unicycles
 * Copyright (C) 2026  Gauthier LE BARTZ LYAN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.eucsoh.android.visualization

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.zip.ZipInputStream

sealed class ImportResult {
    data class Success(val wheelMac: String) : ImportResult()
    data class Error(val reason: String) : ImportResult()
}

class ArchiveImportService(private val context: Context) {

    companion object {
        private const val TAG = "ArchiveImportService"
        private const val MANIFEST_ENTRY = "manifest.json"
    }

    /**
     * Imports a ZIP archive selected via SAF.
     *
     * Steps:
     * 1. Open ZIP from [zipUri]
     * 2. Read manifest.json — if absent: Error("manifest_missing")
     * 3. Parse JSON into ArchiveManifest
     * 4. Verify HMAC — if invalid: Error("hmac_invalid")
     * 5. Verify SHA256 of each declared file — if mismatch: Error("file_corrupted")
     * 6. Extract all files (except manifest.json) into [destUri] (SAF DocumentFile)
     * 7. Return Success(wheelMac)
     */
    suspend fun import(zipUri: Uri, destUri: Uri, onProgress: (Float) -> Unit = {}): ImportResult = withContext(Dispatchers.IO) {
        try {
            // First pass: read manifest and collect file hashes
            val manifestJson: String
            val fileHashes = mutableMapOf<String, String>()
            val fileContents = mutableMapOf<String, ByteArray>()

            context.contentResolver.openInputStream(zipUri)?.use { rawStream ->
                ZipInputStream(rawStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val bytes = zis.readBytes()
                            if (entry.name == MANIFEST_ENTRY) {
                                fileContents[MANIFEST_ENTRY] = bytes
                            } else {
                                fileContents[entry.name] = bytes
                                val sha256 = MessageDigest.getInstance("SHA-256")
                                    .digest(bytes)
                                    .joinToString("") { "%02x".format(it) }
                                fileHashes[entry.name] = sha256
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return@withContext ImportResult.Error("manifest_missing")

            // Step 2: Check manifest exists
            val manifestBytes = fileContents[MANIFEST_ENTRY]
                ?: return@withContext ImportResult.Error("manifest_missing")
            manifestJson = String(manifestBytes, Charsets.UTF_8)

            // Step 3: Parse manifest
            val manifest = try {
                ArchiveManifest.fromJson(manifestJson)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse manifest: ${e.message}")
                return@withContext ImportResult.Error("manifest_missing")
            }

            // Step 4: Verify HMAC
            if (!manifest.verify(ArchiveHmacKey.SECRET)) {
                Log.w(TAG, "HMAC verification failed")
                return@withContext ImportResult.Error("hmac_invalid")
            }

            // Step 5: Verify SHA256 of each declared file
            for (fileEntry in manifest.files) {
                val actualHash = fileHashes[fileEntry.name]
                if (actualHash == null) {
                    Log.w(TAG, "File declared in manifest but missing from ZIP: ${fileEntry.name}")
                    return@withContext ImportResult.Error("file_corrupted")
                }
                if (actualHash != fileEntry.sha256) {
                    Log.w(TAG, "SHA256 mismatch for ${fileEntry.name}: expected=${fileEntry.sha256}, actual=$actualHash")
                    return@withContext ImportResult.Error("file_corrupted")
                }
            }

            // Step 6: Extract all files (except manifest.json) into destUri
            val destDoc = DocumentFile.fromTreeUri(context, destUri)
                ?: return@withContext ImportResult.Error("file_corrupted")

            val filesToExtract = fileContents.entries.filter { it.key != MANIFEST_ENTRY }
            val total = filesToExtract.size.coerceAtLeast(1)
            filesToExtract.forEachIndexed { index, (name, bytes) ->
                writeFileToDocumentTree(destDoc, name, bytes)
                onProgress((index + 1).toFloat() / total)
            }

            Log.d(TAG, "Import successful: wheelMac=${manifest.wheelMac}")
            ImportResult.Success(manifest.wheelMac)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: ${e.message}", e)
            ImportResult.Error("file_corrupted")
        }
    }

    /**
     * Reads the manifest from a ZIP without extracting files.
     * Used to display confirmation info before import.
     */
    suspend fun readManifest(zipUri: Uri): ArchiveManifest? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(zipUri)?.use { rawStream ->
                ZipInputStream(rawStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == MANIFEST_ENTRY) {
                            val bytes = zis.readBytes()
                            zis.closeEntry()
                            return@withContext ArchiveManifest.fromJson(String(bytes, Charsets.UTF_8))
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read manifest: ${e.message}")
            null
        }
    }

    /**
     * Writes a file into the SAF document tree, creating subdirectories as needed.
     */
    private fun writeFileToDocumentTree(root: DocumentFile, relativePath: String, bytes: ByteArray) {
        val segments = relativePath.split("/")
        var current = root

        // Create directories for all segments except the last (which is the file)
        for (i in 0 until segments.size - 1) {
            val dirName = segments[i]
            current = current.findFile(dirName)
                ?: current.createDirectory(dirName)
                ?: throw IllegalStateException("Cannot create directory: $dirName")
        }

        val fileName = segments.last()
        val mimeType = when {
            fileName.endsWith(".pdf") -> "application/pdf"
            fileName.endsWith(".csv") -> "text/csv"
            fileName.endsWith(".dbb") -> "application/octet-stream"
            else -> "application/octet-stream"
        }

        val existingFile = current.findFile(fileName)
        existingFile?.delete()

        val newFile = current.createFile(mimeType, fileName)
            ?: throw IllegalStateException("Cannot create file: $fileName")

        context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
            out.write(bytes)
        }
    }
}
