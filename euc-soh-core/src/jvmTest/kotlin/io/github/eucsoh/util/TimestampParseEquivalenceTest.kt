package io.github.eucsoh.util

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins what [DateTimeUtils.parseToEpochSeconds] returns, bit for bit.
 *
 * Timestamp parsing is the most called operation of the whole analysis — one call per
 * sample row — so it grew a scanner that reads the shape directly instead of running
 * ISO_PATTERN. The scanner is only allowed to answer for shapes the pattern accepts, and
 * hands anything else back to it, so the pattern stays the definition of correct. The
 * original regex body is kept here verbatim as [ReferenceTimestamp] and every string
 * below is run through both.
 */
class TimestampParseEquivalenceTest {

    @Test
    fun `fast scanner matches the regex on generated timestamps`() {
        val dates = listOf(
            "2024-01-01", "2024-12-31", "2024-02-29", "2023-02-29", "1970-01-01",
            "2000-02-29", "1969-12-31", "1900-01-01", "2100-03-14", "0001-01-01",
            "2024-13-45", "2024-00-00"
        )
        val times = listOf("00:00", "23:59", "10:20", "00:00:00", "23:59:59", "10:20:30")
        val fractions = listOf("", ".5", ".05", ".123", ".123456789", ".1234567890", ".", ",5", ",25")
        val offsets = listOf("", "Z", "z", "+02:00", "-02:00", "+0200", "-0530", "+13:45", "+00:00")

        var checked = 0
        for (d in dates) {
            for (sep in listOf('T', ' ', 'X')) {
                for (t in times) {
                    for (frac in fractions) {
                        for (off in offsets) {
                            for (pad in listOf("", " ", "  ", "\t")) {
                                val s = "$d$sep$t$frac$pad$off"
                                assertEquals(
                                    ReferenceTimestamp.parse(s),
                                    DateTimeUtils.parseToEpochSeconds(s),
                                    "divergence on '$s'"
                                )
                                checked++
                            }
                        }
                    }
                }
            }
        }
        println("✓ timestamp scanner identical on $checked generated strings")
        assertTrue(checked > 20_000)
    }

    @Test
    fun `fast scanner matches the regex on malformed and non ISO strings`() {
        val junk = listOf(
            "", " ", "   ", "null", "NaN", "0", "-1", "1700000000", "1700000000000",
            "17000000000000", "-1700000000", "1e9", "2024", "2024-01", "2024-01-01",
            "2024-01-01T", "2024-01-01T10", "2024-01-01T10:0", "24-01-01T10:00:00",
            "20240-01-01T10:00:00", "2024-1-1T10:00:00", "2024-01-01 10:00:00.000Z",
            "2024-01-01T10:00:00.000000Z", "2024-01-01T10:00:00+02", "2024-01-01T10:00:00+2:00",
            "2024-01-01T10:00:00Z+02:00", "2024-01-01T10:00:00:00", "2024-01-01T10:00:0Z",
            "2024-01-01T10:00:00ZZ", "2024-01-01T10:00:00 z", "2024-01-01T10:00:00-00:60",
            "2024-01-01t10:00:00", "2024/01/01 10:00:00", "10:00:00", "2024-01-01T10:00:00,5",
            "2024-01-01T10:00:00.1234567890123", "2024-01-01T10:00:00 + 02:00",
            "2024-01-01T10:00:00\t+02:00", "2024-01-01T10:00:00  ", "0000-00-00T00:00:00",
            "9999-12-31T23:59:59Z", "2024-01-01T10:00:00,5+02:00", "٢٠٢٤-01-01T10:00:00"
        )
        junk.forEach { s ->
            assertEquals(ReferenceTimestamp.parse(s), DateTimeUtils.parseToEpochSeconds(s), "on '$s'")
        }
    }

    @Test
    fun `fast scanner matches the regex on real corpus timestamps`() {
        val files = corpusFiles()
        if (files.isEmpty()) {
            println("SAMPLES folder not found - skipping the corpus half of the timestamp check")
            return
        }
        val stamps = LinkedHashSet<String>()
        for (f in files) {
            f.useLines { lines ->
                lines.take(400).forEach { line ->
                    line.split(',').forEach { cell ->
                        val c = cell.trim()
                        if (c.length >= 16 && c.contains('-') && c.contains(':')) stamps += c
                    }
                }
            }
        }
        assertTrue(stamps.isNotEmpty(), "no timestamp found in ${files.size} corpus logs")
        stamps.forEach { s ->
            assertEquals(ReferenceTimestamp.parse(s), DateTimeUtils.parseToEpochSeconds(s), "on '$s'")
        }
        println("✓ timestamp scanner identical on ${stamps.size} distinct real timestamps")
    }

    /**
     * The equivalence tests stay green even if the scanner never matches, because it hands
     * everything to the pattern. This one does not: it fails if a shape the logs actually
     * write stops being handled without regex.
     */
    @Test
    fun `fast scanner covers the shapes the logs write`() {
        val real = listOf(
            "2025-07-15T16:56:28.521+0700",      // EUC World, the common one
            "2026-02-11 14:15:35",               // WheelLog, date and time joined
            "2025-07-15T16:56:28",
            "2025-07-15T16:56:28Z",
            "2025-07-15T16:56:28.521Z",
            "2025-07-15T16:56:28.521-05:30",
            "2025-07-15T16:56:28,521 +02:00"
        )
        real.forEach {
            assertNotNull(
                DateTimeUtils.parseIsoFast(it),
                "fast scanner dropped '$it' and fell back to the regex"
            )
        }
    }

    private fun corpusFiles(): List<File> {
        val root = samplesRoot() ?: return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.extension.equals("csv", true) }
            .toList()
            .sortedByDescending { it.length() }
            .take(6)
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
 * The timestamp parser exactly as it was before the scanner was added: regex match plus
 * group extraction. Verbatim copy — do not "improve" it, it is the reference the live
 * scanner is judged against.
 */
private object ReferenceTimestamp {

    private val ISO_PATTERN = Regex(
        """^(\d{4})-(\d{2})-(\d{2})([T ])(\d{2}):(\d{2})(?::(\d{2}))?(?:[.,](\d{1,9}))?\s*(Z|[+-]\d{2}:?\d{2})?$"""
    )

    fun parse(raw: String): Double? {
        val s = raw.trim()
        if (s.isEmpty()) return null

        s.toLongOrNull()?.let { digits ->
            return if (s.length >= 13) digits / 1000.0 else digits.toDouble()
        }

        val m = ISO_PATTERN.matchEntire(s) ?: return null
        val (year, month, day) = Triple(
            m.groupValues[1].toInt(),
            m.groupValues[2].toInt(),
            m.groupValues[3].toInt()
        )
        val hour = m.groupValues[5].toInt()
        val minute = m.groupValues[6].toInt()
        val second = m.groupValues[7]?.toIntOrNull() ?: 0
        val fraction = m.groupValues[8]?.let {
            it.padEnd(9, '0').take(9).toDouble() / 1_000_000_000.0
        } ?: 0.0
        val offsetSeconds = parseOffset(m.groupValues[9])

        val days = daysFromCivil(year, month, day)
        return days * 86400.0 + hour * 3600 + minute * 60 + second + fraction - offsetSeconds
    }

    private fun parseOffset(offset: String?): Double {
        if (offset == null || offset.isEmpty() || offset == "Z" || offset == "z") return 0.0
        val sign = if (offset[0] == '-') -1 else 1
        val digits = offset.substring(1).replace(":", "")
        val hours = digits.take(2).toInt()
        val minutes = if (digits.length >= 4) digits.substring(2, 4).toInt() else 0
        return sign * (hours * 3600 + minutes * 60).toDouble()
    }

    private fun daysFromCivil(year: Int, month: Int, day: Int): Int {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }
}
