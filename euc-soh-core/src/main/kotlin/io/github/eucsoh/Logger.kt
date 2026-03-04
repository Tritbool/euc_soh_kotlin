package io.github.eucsoh

/**
 * Simple logging interface.
 */
interface Logger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

/**
 * No-op logger.
 */
object NoOpLogger : Logger {
    override fun d(tag: String, message: String) {}
    override fun e(tag: String, message: String, throwable: Throwable?) {}
}
