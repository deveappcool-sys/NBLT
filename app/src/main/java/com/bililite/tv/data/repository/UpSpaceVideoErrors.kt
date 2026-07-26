package com.bililite.tv.data.repository

object UpSpaceVideoErrors {
    const val RATE_LIMIT_MESSAGE = "\u8bf7\u6c42\u592a\u9891\u7e41\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5"
    const val ACCESS_DENIED_MESSAGE = "\u8bbf\u95ee\u6743\u9650\u4e0d\u8db3\uff0c\u6295\u7a3f\u5217\u8868\u6682\u65f6\u65e0\u6cd5\u52a0\u8f7d"
    const val RETRY_COOLDOWN_MESSAGE = "\u8bf7\u7a0d\u540e\u518d\u8bd5"
    const val RETRY_COOLDOWN_MS = 10_000L

    fun isRateLimitedMessage(message: String?): Boolean {
        val text = message.orEmpty()
        return text.contains("\u9891\u7e41") ||
            text.contains("\u592a\u5feb") ||
            text.contains("\u592a\u9891\u7e41")
    }

    fun normalizeVideoErrorMessage(message: String?, defaultMessage: String): String {
        val text = message.orEmpty()
        return if (text.isBlank()) defaultMessage else text
    }

    fun mapUpVideoApiError(code: Int, message: String?): String {
        val text = message.orEmpty()
        return when (code) {
            -403 -> ACCESS_DENIED_MESSAGE
            -799 -> RATE_LIMIT_MESSAGE
            else -> when {
                isRateLimitedMessage(text) -> RATE_LIMIT_MESSAGE
                text.isNotBlank() -> text
                else -> "\u6295\u7a3f\u5217\u8868\u52a0\u8f7d\u5931\u8d25\uff08$code\uff09"
            }
        }
    }
}
