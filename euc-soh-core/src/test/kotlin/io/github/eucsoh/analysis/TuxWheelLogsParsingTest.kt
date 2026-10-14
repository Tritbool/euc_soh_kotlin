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
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Separate TuxWheel regressions, reusing WheelLog fixtures while their CSV formats match.
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

    private fun copyRealLog(csv: File) {
        val fixture = File("src/test/resources/real_logs/wheellog.csv")
        assertTrue(fixture.isFile, "WheelLog fixture reused for TuxWheel must exist")
        fixture.copyTo(csv)
    }

    @Test
    fun `TuxWheel log parses and computes positive realistic Req`() = withTuxWheelLog { csv ->
        copyRealLog(csv)

        val result = ReqStatsComputer.computeReqStatsForFile(
            csvPath = csv.absolutePath,
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
    fun `TuxWheel log extracts distance and combined datetime`() = withTuxWheelLog { csv ->
        copyRealLog(csv)

        val result = ReqStatsComputer.computeReqStatsForFile(csv.absolutePath)

        assertNotNull(result)
        assertEquals("tuxwheel", result.source)
        assertEquals("totaldistance_m_wl", result.wheelKmSource)
        assertTrue(assertNotNull(result.wheelKm) > 0.0)
        assertEquals("2026-03-03 07:59:26.334", result.datetimeFirst)
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
