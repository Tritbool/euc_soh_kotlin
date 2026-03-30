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

package io.github.eucsoh.android.data.scanner

import androidx.documentfile.provider.DocumentFile

/**
 * Extension functions for DocumentFile to enable recursive traversal.
 * Similar to File.walkTopDown() but for SAF URIs.
 */

/**
 * Walks through the DocumentFile tree recursively.
 * 
 * @param maxDepth Maximum depth to traverse (default 10)
 * @return Sequence of all DocumentFiles found
 */
fun DocumentFile.walkTopDown(maxDepth: Int = 10): Sequence<DocumentFile> {
    return sequence {
        walkRecursive(this@walkTopDown, 0, maxDepth)
    }
}

private suspend fun SequenceScope<DocumentFile>.walkRecursive(
    file: DocumentFile,
    currentDepth: Int,
    maxDepth: Int
) {
    yield(file)
    
    if (currentDepth >= maxDepth) return
    if (!file.isDirectory) return
    
    file.listFiles().forEach { child ->
        walkRecursive(child, currentDepth + 1, maxDepth)
    }
}

/**
 * Lists all files in directory (non-recursive).
 */
fun DocumentFile.listFilesRecursive(maxDepth: Int = 10): List<DocumentFile> {
    return walkTopDown(maxDepth).toList()
}

/**
 * Finds all CSV files recursively.
 */
fun DocumentFile.findCsvFiles(maxDepth: Int = 10): List<DocumentFile> {
    return walkTopDown(maxDepth)
        .filter { it.isFile && it.name?.endsWith(".csv", ignoreCase = true) == true }
        .toList()
}

/**
 * Finds directories with specific name (case-insensitive).
 */
fun DocumentFile.findDirectoriesNamed(name: String, maxDepth: Int = 10): List<DocumentFile> {
    return walkTopDown(maxDepth)
        .filter { it.isDirectory && it.name?.equals(name, ignoreCase = true) == true }
        .toList()
}
