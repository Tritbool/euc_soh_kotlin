// AnalyzerIntegrationTest.kt
package com.euc.soh.analysis

import com.euc.soh.io.FileProvider
import com.euc.soh.model.LogSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.io.File

class AnalyzerIntegrationTest : FunSpec({
    test("analyzeFolderForReq should produce WheelStatistics with alarms and thresholds via FileProvider") {
        // create two small CSV files in build/tmp for testing
        val dir = File("build/tmp/test_logs")
        dir.mkdirs()

        val csv1 = File(dir, "log1.csv")
        csv1.writeText("datetime, distance_total, voltage, current, speed, system_temp\n")
        csv1.appendText("2020-01-01T00:00:00Z, 1000, 42.0, 10.0, 25.0, 30.0\n")
        csv1.appendText("2020-01-01T00:00:01Z, 1000, 41.5, 12.0, 30.0, 30.0\n")

        val csv2 = File(dir, "log2.csv")
        csv2.writeText("datetime, distance_total, voltage, current, speed, system_temp\n")
        // Add an idle high-voltage low-current point to create a high vIdle
        csv2.appendText("2020-02-01T00:00:00Z, 6000000, 52.0, 1.0, 0.0, 60.0\n")
        // make large sag with moderate current so Req is high
        csv2.appendText("2020-02-01T00:00:01Z, 6000000, 30.0, 10.0, 30.0, 60.0\n")
        csv2.appendText("2020-02-01T00:00:02Z, 6000000, 29.0, 12.0, 35.0, 60.0\n")

        val provider = object : FileProvider {
            override fun getFiles(): List<String> = listOf(csv1.absolutePath, csv2.absolutePath)
        }

        val stats = Analyzer.analyzeFolderForReq("wheelA", provider)

        stats.logs.shouldNotBeEmpty()
        stats.alarms.shouldNotBeEmpty()

        // cleanup
        csv1.delete()
        csv2.delete()
        dir.delete()
    }
})
