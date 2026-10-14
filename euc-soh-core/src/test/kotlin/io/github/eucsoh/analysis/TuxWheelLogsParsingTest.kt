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

package io.github.eucsoh.analysis

import io.github.eucsoh.Constants.KNOWN_SERIES
import io.github.eucsoh.Constants.MAXIIMAL_CELL_V
import io.github.eucsoh.CsvSource
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Separate TuxWheel regressions using tuxwheel.csv and synthetic timestamp fixtures.
 */
class TuxWheelLogsParsingTest {

    private fun withTuxWheelLog(test: (File) -> Unit) {
        val root = createTempDirectory("tuxwheel_logs_").toFile()
        try {
            val folder = File(root, "TuxWheel")
            assertTrue(folder.mkdir())
            test(File(folder, "log.csv"))
        } finally {
            root.deleteRecursively()
        }
    }

    private val tuxwheelFile = File("src/test/resources/real_logs/tuxwheel.csv")

    // Preserve the source folder identity without copying the resource file.
    private val tuxwheelPath = "TuxWheel/tuxwheel.csv"
    private val tuxwheelSource = object : CsvSource {
        override fun openCsvStream(path: String) = tuxwheelFile.inputStream()
        override fun listCsvFiles(folderPath: String) = listOf(tuxwheelPath)
    }

    @Test
    fun `TuxWheel log parses and computes positive realistic Req`() {
        if (!tuxwheelFile.exists()) {
            println("Skipping: tuxwheel.csv not found")
            return
        }

        val result = ReqStatsComputer.computeReqStatsForFile(
            csvPath = tuxwheelPath,
            csvSource = tuxwheelSource,
            speedThr = 20.0,
            curThr = 5.0
        )

        assertNotNull(result, "TuxWheel log should parse successfully")
        assertEquals("tuxwheel", result.source)
        assertTrue((result.nPoints ?: 0) > 50, "Should have sufficient data points")
        assertTrue((result.reqMedian ?: 0.0) in 0.001..0.5, "Req median should be realistic")
        assertTrue((result.reqMean ?: 0.0) > 0.0, "Req mean must be positive")
        assertTrue((result.req95p ?: 0.0) > 0.0, "Req 95p must be positive")
        assertTrue((result.vIdle ?: 0.0) in 50.0..KNOWN_SERIES.last() * MAXIIMAL_CELL_V)
        assertTrue(assertNotNull(result.ns) in KNOWN_SERIES)
    }

    @Test
    fun `TuxWheel log extracts distance and combined datetime`() {
        if (!tuxwheelFile.exists()) {
            println("Skipping: tuxwheel.csv not found")
            return
        }

        val result = ReqStatsComputer.computeReqStatsForFile(tuxwheelPath, tuxwheelSource)

        assertNotNull(result)
        assertEquals("tuxwheel", result.source)
        assertEquals("totaldistance_m_wl", result.wheelKmSource)
        assertTrue(assertNotNull(result.wheelKm) > 0.0)
        assertTrue(assertNotNull(result.datetimeFirst).contains(" "), "Should combine date and time")
    }

    @Test
    fun `TuxWheel valid timestamps use trapezoidal phase current integration`() = withTuxWheelLog { csv ->
        IPhaseIntegrationRegressionTest.writeWheelLogCsv(
            csv,
            IPhaseIntegrationRegressionTest.buildWheelLogTimestamps()
        )

        val result = ReqStatsComputer.computeReqStatsForFile(csv.absolutePath)

        assertNotNull(result)
        assertEquals("tuxwheel", result.source)
        assertEquals(false, result.fallbackDtUsed)
        assertEquals(
            IPhaseIntegrationRegressionTest.EXPECTED_REAL_TS,
            assertNotNull(result.iPhase2Int),
            1.0
        )
    }

    @Test
    fun `TuxWheel broken timestamps fall back to fixed time steps`() = withTuxWheelLog { csv ->
        IPhaseIntegrationRegressionTest.writeWheelLogCsv(csv, null)

        val result = ReqStatsComputer.computeReqStatsForFile(csv.absolutePath)

        assertNotNull(result)
        assertEquals("tuxwheel", result.source)
        assertEquals(true, result.fallbackDtUsed)
        assertEquals(
            IPhaseIntegrationRegressionTest.EXPECTED_FALLBACK,
            assertNotNull(result.iPhase2Int),
            1.0
        )
    }
}
