package io.github.eucsoh.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.github.eucsoh.SohAnalyzer
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnSelectCsv: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnSelectCsv = findViewById(R.id.btnSelectCsv)

        tvStatus.text = "EUC SoH - Kotlin\n\nReady to analyze CSV files"

        btnSelectCsv.setOnClickListener {
            openFilePicker()
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        startActivityForResult(intent, REQUEST_CSV)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CSV && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                tvStatus.text = "Selected: $uri\n\nAnalyzing..."
                // TODO: Implement CSV analysis with SohAnalyzer
            }
        }
    }

    companion object {
        private const val REQUEST_CSV = 1
    }
}
