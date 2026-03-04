package io.github.eucsoh.android

import android.util.Log
import io.github.eucsoh.Logger

/**
 * Android-specific logger implementation using android.util.Log.
 * Logs will appear in logcat and can be filtered by tag.
 */
class AndroidLogger : Logger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }
    
    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
