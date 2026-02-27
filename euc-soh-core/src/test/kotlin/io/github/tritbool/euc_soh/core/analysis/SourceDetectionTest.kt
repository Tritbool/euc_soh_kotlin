package io.github.tritbool.euc_soh.core.analysis

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf

class SourceDetectionTest : FunSpec({

    test("detectSource identifies EUC World format with datetime and distance_total") {
        val df = dataFrameOf(
            "datetime", "distance_total", "voltage"
        )(
            "2024-01-01T10:00:00", 1234.5, 132.4
        )
        detectSource(df) shouldBe "euc_world"
    }

    test("detectSource identifies WheelLog format with date, time, totaldistance") {
        val df = dataFrameOf(
            "date", "time", "totaldistance", "voltage"
        )(
            "2024-01-01", "10:00:00", 1234500, 132.4
        )
        detectSource(df) shouldBe "wheellog"
    }

    test("detectSource defaults to euc_world if only datetime present") {
        val df = dataFrameOf(
            "datetime", "voltage", "current"
        )(
            "2024-01-01T10:00:00", 132.4, 25.0
        )
        detectSource(df) shouldBe "euc_world"
    }

    test("detectSource defaults to wheellog if no datetime columns") {
        val df = dataFrameOf(
            "voltage", "current", "speed"
        )(
            132.4, 25.0, 30.0
        )
        detectSource(df) shouldBe "wheellog"
    }

    test("normalizeDistanceTotal extracts distance_total in km for euc_world") {
        val df = dataFrameOf(
            "datetime", "distance_total"
        )(
            "2024-01-01T10:00:00", 1234.5,
            "2024-01-01T10:01:00", 1235.2
        )
        val (dist, src) = normalizeDistanceTotal(df, "euc_world")
        dist shouldBe 1235.2
        src shouldBe "distance_total_km_euc"
    }

    test("normalizeDistanceTotal converts totaldistance from meters to km for wheellog") {
        val df = dataFrameOf(
            "date", "time", "totaldistance"
        )(
            "2024-01-01", "10:00:00", 1234500,
            "2024-01-01", "10:01:00", 1235200
        )
        val (dist, src) = normalizeDistanceTotal(df, "wheellog")
        dist shouldBe 1235.2
        src shouldBe "totaldistance_m_wl"
    }
})
