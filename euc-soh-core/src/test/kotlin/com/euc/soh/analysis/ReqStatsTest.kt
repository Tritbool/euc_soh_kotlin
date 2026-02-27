// ReqStatsTest.kt
package com.euc.soh.analysis

import com.euc.soh.io.FileProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

class ReqStatsTest : FunSpec({

    fun createTempCsv(name: String, content: String): File {
        val dir = File("build/tmp/req_stats_test")
        dir.mkdirs()
        val f = File(dir, name)
        f.writeText(content)
        return f
    }

    afterSpec {
        File("build/tmp/req_stats_test").deleteRecursively()
    }

    test("analyzeLogForReq returns null for non-existent file") {
        val result = Analyzer.analyzeLogForReq("/nonexistent/file.csv")
        result shouldBe null
    }

    test("analyzeLogForReq returns null for empty CSV") {
        val f = createTempCsv("empty.csv", "datetime,voltage,current,speed,system_temp\n")
        val result = Analyzer.analyzeLogForReq(f.absolutePath)
        result shouldBe null
    }

    test("analyzeLogForReq computes valid Req stats from minimal EUC World CSV") {
        // Build a CSV with enough points to compute Req
        val sb = StringBuilder("datetime,distance_total,voltage,current,speed,system_temp\n")
        // idle point to establish v_idle
        sb.appendLine("2024-01-01T10:00:00,1000.0,84.0,0.5,0.0,30.0")
        // 60 load points: high speed, current 15A, so |sag|=2V -> Req=0.133
        repeat(60) { i ->
            val t = "2024-01-01T10:0${i / 60}:${"%02d".format(i % 60)}"
            sb.appendLine("$t,1000.0,82.0,15.0,30.0,30.0")
        }
        val f = createTempCsv("euc_world.csv", sb.toString())
        val result = Analyzer.analyzeLogForReq(f.absolutePath)

        result shouldNotBe null
        result!!.reqMedian shouldBeGreaterThan 0.0
        result.reqMedian shouldBeLessThan 2.0   // sanity: < 2 Ohm
        result.sag95p shouldBeGreaterThan 0.0
        result.nPoints shouldBeGreaterThan 0
    }

    test("analyzeLogForReq detects higher Req when sag is larger") {
        val header = "datetime,distance_total,voltage,current,speed,system_temp\n"

        // Low sag: v_idle ~84V, measured 82V at 15A -> Req = 2/15 ≈ 0.133 Ω
        val lowSagCsv = buildString {
            append(header)
            appendLine("2024-01-01T10:00:00,1000.0,84.0,0.5,0.0,30.0")
            repeat(60) { appendLine("2024-01-01T10:00:01,1000.0,82.0,15.0,30.0,30.0") }
        }

        // High sag: v_idle ~84V, measured 72V at 15A -> Req = 12/15 = 0.8 Ω
        val highSagCsv = buildString {
            append(header)
            appendLine("2024-01-01T10:00:00,2000.0,84.0,0.5,0.0,30.0")
            repeat(60) { appendLine("2024-01-01T10:00:01,2000.0,72.0,15.0,30.0,30.0") }
        }

        val fLow = createTempCsv("low_sag.csv", lowSagCsv)
        val fHigh = createTempCsv("high_sag.csv", highSagCsv)

        val resultLow = Analyzer.analyzeLogForReq(fLow.absolutePath)
        val resultHigh = Analyzer.analyzeLogForReq(fHigh.absolutePath)

        resultLow shouldNotBe null
        resultHigh shouldNotBe null
        resultHigh!!.reqMedian shouldBeGreaterThan resultLow!!.reqMedian
    }

    test("analyzeFolderForReq aggregates multiple CSVs via FileProvider") {
        val header = "datetime,distance_total,voltage,current,speed,system_temp\n"
        val csv1 = createTempCsv("log_a.csv", buildString {
            append(header)
            appendLine("2024-01-01T10:00:00,1000.0,84.0,0.5,0.0,30.0")
            repeat(60) { appendLine("2024-01-01T10:00:01,1000.0,82.0,15.0,30.0,30.0") }
        })
        val csv2 = createTempCsv("log_b.csv", buildString {
            append(header)
            appendLine("2024-02-01T10:00:00,2000.0,84.0,0.5,0.0,35.0")
            repeat(60) { appendLine("2024-02-01T10:00:01,2000.0,79.0,15.0,30.0,35.0") }
        })

        val provider = object : FileProvider {
            override fun getFiles() = listOf(csv1.absolutePath, csv2.absolutePath)
        }

        val stats = Analyzer.analyzeFolderForReq("TestWheel", provider)
        stats.logs.size shouldBe 2
    }
})
