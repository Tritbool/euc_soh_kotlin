// MainActivity.kt
package com.euc.soh.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Point d'entrée Android - Hello World minimal.
 * L'analyse SoH est déléguée au module euc-soh-core via AndroidCsvOpener.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
