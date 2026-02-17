// ArrheniusNormalizerTest.kt
package com.euc.soh.analysis

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class ArrheniusNormalizerTest : FunSpec({
    
    test("normalizeRBattTo25C should return same value at 25°C") {
        val rMeasured = 0.050
        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, 25.0)
        
        result shouldBe (rMeasured plusOrMinus 0.0001)
    }

    test("normalizeRBattTo25C should increase resistance for hot temperature") {
        val rMeasured = 0.080
        val tempHot = 60.0

        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, tempHot)

        // À chaud, la résistance mesurée est plus basse qu'à 25°C,
        // donc la résistance normalisée doit être plus haute
        result shouldBeGreaterThan rMeasured
    }

    test("normalizeRBattTo25C should decrease resistance for cold temperature") {
        val rMeasured = 0.060
        val tempCold = -5.0

        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, tempCold)

        // À froid, la résistance mesurée est plus haute qu'à 25°C,
        // donc la résistance normalisée doit être plus basse
        result shouldBeLessThan rMeasured
    }
    
    test("normalizeRBattTo25C should handle null temperature") {
        val rMeasured = 0.050
        
        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, null)
        
        result shouldBe rMeasured
    }
    
    test("normalizeRBattTo25C should handle NaN resistance") {
        val result = ArrheniusNormalizer.normalizeRBattTo25C(Double.NaN, 25.0)
        
        result.isNaN() shouldBe true
    }
    
    test("normalizeRBattTo25C should compute correction for extreme temperatures (match Python)") {
        val rMeasured = 0.050
        val tempExtreme = 100.0 // Hors plage fiable, mais on calcule la correction comme en Python

        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, tempExtreme)
        
        // On s'attend à une correction vers 25°C (valeur normalisée > mesurée pour T très chaude)
        result shouldBeGreaterThan rMeasured
    }
    
    test("normalizeRBattTo25C with custom Ea should affect result") {
        val rMeasured = 0.080
        val temp = 45.0
        
        val resultDefaultEa = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, temp, 20000.0)
        val resultHighEa = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, temp, 40000.0)

        // Avec Ea plus élevé, l'effet thermique est plus fort,
        // donc la correction vers 25°C est plus grande → R25 plus élevée
        resultHighEa shouldBeGreaterThan resultDefaultEa
    }
    
    test("calibrateEaFromLogs should return default with insufficient data") {
        val emptyLogs = emptyList<com.euc.soh.model.LogData>()
        
        val result = ArrheniusNormalizer.calibrateEaFromLogs(emptyLogs)
        
        result shouldBe 20000.0 // DEFAULT_EA
    }
    
    test("normalizeRBattTo25C should always return non-negative") {
        val rMeasured = 0.001 // Très faible
        val temp = 60.0
        
        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, temp)
        
        result shouldBeGreaterThan 0.0
    }
})
