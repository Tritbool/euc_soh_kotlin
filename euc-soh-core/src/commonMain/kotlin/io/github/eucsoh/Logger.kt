package io.github.eucsoh

/**
 * Cross-platform logging interface.
 * Allows platform-specific implementations (Android Log, println, etc.)
 */
interface Logger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

/**
 * No-op logger for platforms without logging infrastructure.
 */
object NoOpLogger : Logger {
    override fun d(tag: String, message: String) {}
    override fun e(tag: String, message: String, throwable: Throwable?) {}
}
