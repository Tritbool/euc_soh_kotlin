package io.github.eucsoh.frame

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins what [LogFrame.readCsv] produces, cell by cell.
 *
 * The parser was rewritten to walk the text once instead of building a grid of cells
 * first, and the value inference stopped paying for a thrown NumberFormatException on
 * every non-numeric cell. Both are only acceptable as long as no value a log can hold
 * changes meaning, so the pre-rewrite parser is kept here verbatim as [ReferenceCsv]
 * and compared against the live one — on hand-built edge cases and on corpus logs.
 */
class CsvParserEquivalenceTest {

    @Test
    fun `live parser matches the pre-rewrite parser on edge cases`() {
        val cases = mapOf(
            "plain" to "a,b,c\n1,2.5,x\n",
            "no trailing newline" to "a,b\n1,2",
            "crlf" to "a,b\r\n1,2\r\n",
            "blank lines between rows" to "a,b\n\n1,2\n\n\n3,4\n",
            "trailing blank line" to "a,b\n1,2\n\n",
            "all blank row" to "a,b\n,,\n1,2\n",
            "empty header cell" to "a,,b\n1,2,3\n",
            "header only" to "a,b\n",
            "empty text" to "",
            "only newlines" to "\n\n\n",
            "ragged short row" to "a,b,c\n1\n",
            "ragged long row" to "a,b\n1,2,3,4\n",
            "quoted comma" to "a,b\n\"x,y\",2\n",
            "quoted newline" to "a,b\n\"line1\nline2\",2\n",
            "escaped quotes" to "a,b\n\"he said \"\"hi\"\"\",2\n",
            "quoted then blank" to "a,b\n\"\",2\n",
            "whitespace padded cells" to " a , b \n  1 ,  2.5 \n",
            "empty cells" to "a,b,c\n,,\n",
            "numeric shapes" to "a,b,c,d,e,f,g,h,i,j,k,l,m,n\n" +
                "1,-2,+3,.5,-.5,1.5,1e5,1E-5,1e,1.2.3,007," +
                "9223372036854775807,9223372036854775808,1.7976931348623157E308\n",
            "word shapes" to "a,b,c,d,e,f,g,h,i\n" +
                "true,TRUE,True,false,FALSE,False,NaN,Infinity,-Infinity\n",
            "hex and suffix" to "a,b,c,d\n0x1.8p3,1.0d,1.0f,0x10\n",
            "scientific in text" to "a,b\n3.5e,abc\n",
            "leading zero strings" to "a,b\n007,0\n",
            "semicolon not a separator" to "a;b,c\n1;2,3\n"
        )
        cases.forEach { (name, text) -> assertSameParse(name, text) }
    }

    @Test
    fun `live parser matches the pre-rewrite parser on corpus logs`() {
        val files = corpusFiles()
        if (files.isEmpty()) {
            println("SAMPLES folder not found - skipping the corpus half of the parser check")
            return
        }
        var cells = 0L
        files.forEach { f -> cells += assertSameParse(f.name, f.readText()) }
        println("✓ parser identical on ${files.size} logs, $cells cells")
        assertTrue(cells > 10_000, "expected a real corpus sample, got $cells cells")
    }

    /** Returns the number of cells compared. */
    private fun assertSameParse(name: String, text: String): Long {
        val expected = ReferenceCsv.readCsv(text)
        val actual = LogFrame.readCsv(text)

        assertEquals(
            expected.keys.toList(), actual.columnNames(),
            "[$name] column names or order differ"
        )
        assertEquals(
            expected.values.firstOrNull()?.size ?: 0, actual.rowsCount(), "[$name] row count"
        )

        var cells = 0L
        for ((column, want) in expected) {
            val got = actual[column]
            assertEquals(want.size, got.size, "[$name] column '$column' length")
            want.forEachIndexed { i, w ->
                cells++
                val g = got[i]
                // Type matters: 1L and 1.0 are not the same stored value.
                val same = w == g && (w == null || g == null || w::class == g::class)
                assertTrue(
                    same,
                    "[$name] '$column'[$i]: reference=$w${w?.let { " (${it::class.simpleName})" } ?: ""}" +
                        " live=$g${g?.let { " (${it::class.simpleName})" } ?: ""}"
                )
            }
        }
        return cells
    }

    /** Bounded so the suite stays quick, but wide enough to cross every source format. */
    private fun corpusFiles(): List<File> {
        val root = samplesRoot() ?: return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.extension.equals("csv", true) && it.length() in 1..(8L * 1024 * 1024) }
            .toList()
            .sortedByDescending { it.length() }
            .take(40)
    }

    /** `-Dsoh.samples.dir`, `$SOH_SAMPLES_DIR`, or a `SAMPLES/` folder above the module. */
    private fun samplesRoot(): File? {
        val given = System.getProperty("soh.samples.dir") ?: System.getenv("SOH_SAMPLES_DIR")
        if (given != null && File(given).isDirectory) return File(given)
        var dir: File? = File(".").absoluteFile.canonicalFile
        while (dir != null) {
            val candidate = File(dir, "SAMPLES")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        return null
    }
}


/**
 * The parser exactly as it was before the single-pass rewrite: split the whole text into
 * a grid of rows of cells, keep the non-blank ones, then transpose into columns.
 * Verbatim copy — do not "improve" it, it is the reference the live parser is judged
 * against.
 */
private object ReferenceCsv {

    fun readCsv(text: String): Map<String, List<Any?>> {
        val lines = splitCsvLines(text)
        if (lines.isEmpty()) return emptyMap()

        val header = lines.first()
        val data = lines.drop(1)
        val columns = LinkedHashMap<String, List<Any?>>()
        for ((colIdx, name) in header.withIndex()) {
            val cleanName = name.trim()
            if (cleanName.isEmpty()) continue
            columns[cleanName] = data.map { row ->
                if (colIdx < row.size) inferValue(row[colIdx]) else null
            }
        }
        return columns
    }

    private fun splitCsvLines(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endCell() {
            current.add(sb.toString())
            sb.clear()
        }

        fun endRow() {
            endCell()
            rows.add(current)
            current = mutableListOf()
        }

        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            sb.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        sb.append(ch)
                    }
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> endCell()
                ch == '\n' -> endRow()
                ch == '\r' -> {}
                else -> sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty() || current.isNotEmpty()) endRow()

        return rows.filter { row -> row.any { it.isNotBlank() } }
    }

    private fun inferValue(raw: String): Any? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        if (s.equals("true", ignoreCase = true)) return true
        if (s.equals("false", ignoreCase = false)) return false
        if (s.contains("e") || s.contains("E")) {
            return s.toDoubleOrNull()
        }
        if (s.contains(".")) {
            return s.toDoubleOrNull() ?: s
        }
        s.toLongOrNull()?.let { return it }
        return s
    }
}
