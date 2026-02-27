package io.github.tritbool.euc_soh.android

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Minimal Hello World activity for Android module.
 * TODO: Implement actual EUC SoH analysis UI.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Simple programmatic UI for now
        val textView = TextView(this).apply {
            text = "EUC SoH - Android Module\nCore library loaded successfully!"
            textSize = 18f
            setPadding(32, 32, 32, 32)
        }
        
        setContentView(textView)
    }
}
