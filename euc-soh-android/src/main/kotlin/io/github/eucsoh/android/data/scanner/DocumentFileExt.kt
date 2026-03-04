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
