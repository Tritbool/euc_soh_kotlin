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

package io.github.eucsoh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guard tests for the R_eq metric labels in [Constants.Metrics].
 *
 * R_eq is not a measured resistance: it is inferred from voltage sag under load, which is
 * why the paper calls it the Equivalent Load Proxy (ELP). Two things are frozen here:
 *  - `csv_code`, a stable data contract with user exports and the paper reference files,
 *    which must never drift;
 *  - the human-readable labels, which must keep saying "proxy" and must never go back to
 *    presenting R_eq as a plain "equivalent resistance".
 */
class ConstantsMetricsTest {

    private val reqMetrics = Constants.Metrics.values().filter { it.csv_code.startsWith("Req") }

    @Test
    fun `Req csv codes are the frozen data contract`() {
        assertEquals("Req_median_25C", Constants.Metrics.REQ_MEDIAN_25C.csv_code)
        assertEquals("Req_median", Constants.Metrics.REQ_MEDIAN.csv_code)
        assertEquals("Req_mean", Constants.Metrics.REQ_MEAN.csv_code)
        assertEquals("Req_95p", Constants.Metrics.REQ_95P.csv_code)
        assertEquals(4, reqMetrics.size, "unexpected Req_* metric set")
    }

    @Test
    fun `Req labels present the metric as a load proxy`() {
        for (metric in reqMetrics) {
            val label = metric.label ?: continue
            assertTrue(
                label.contains("R_eq"),
                "${metric.name}: label must keep the R_eq symbol users see elsewhere: $label"
            )
            assertTrue(
                label.contains("proxy", ignoreCase = true),
                "${metric.name}: R_eq is a load proxy, the label must say so: $label"
            )
        }
    }

    @Test
    fun `Req labels never claim a measured equivalent resistance`() {
        for (metric in reqMetrics) {
            val label = metric.label ?: continue
            assertTrue(
                !label.contains("Equivalent resistance", ignoreCase = true),
                "${metric.name}: R_eq is not a measured resistance, do not call it an " +
                    "equivalent resistance: $label"
            )
        }
    }

    @Test
    fun `R_batt labels are untouched, it is a real resistance`() {
        assertEquals("R_batt median @25°C (Ω)", Constants.Metrics.R_BATT_MEDIAN_25C.label)
        assertEquals("R_batt median (Ω)", Constants.Metrics.R_BATT_MEDIAN.label)
    }
}