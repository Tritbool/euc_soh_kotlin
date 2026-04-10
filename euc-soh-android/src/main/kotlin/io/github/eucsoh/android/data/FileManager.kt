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

package io.github.eucsoh.android.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * File management for wheel CSV files.
 *
 * Features:
 * - List CSV files for a wheel
 * - Preview CSV content
 * - Mark files as excluded from analysis
 * - Share files with external apps
 */
class FileManager(private val context: Context) {

    /**
     * Copie le CSV source (URI SAF) dans un fichier temporaire du cache de l'app,
     * puis retourne une URI FileProvider pointant dessus.
     *
     * Pourquoi : une URI SAF (content://com.android.externalstorage/...) ne peut pas être
     * transmise à une app tierce — elle n'a pas de grant actif dessus. On passe par le
     * FileProvider de l'app pour exposer une copie via une URI que toute app peut lire.
     *
     * Le dossier csv_share/ est nettoyé à chaque appel pour éviter l'accumulation de copies.
     */
    suspend fun copyToCache(sourceUri: Uri, fileName: String): Uri = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "csv_share").apply {
            // Nettoie les anciennes copies au passage
            deleteRecursively()
            mkdirs()
        }
        val destFile = File(cacheDir, fileName)
        context.contentResolver.openInputStream(sourceUri)!!.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile
        )
    }

}
