package com.bililite.tv.data.api

class SessionExpiredException(
    message: String = MESSAGE
) : Exception(message) {
    companion object {
        const val MESSAGE = "\u767b\u5f55\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55"

        fun throwIfExpired(code: Int) {
            if (code == -101) {
                throw SessionExpiredException()
            }
        }

        fun isExpired(error: Throwable): Boolean {
            if (error is SessionExpiredException) {
                return true
            }
            val message = error.message.orEmpty()
            return message.contains("Cookie \u5df2\u5931\u6548") ||
                message.contains("\u767b\u5f55\u5df2\u8fc7\u671f") ||
                message.contains("\u8bf7\u91cd\u65b0\u767b\u5f55") ||
                message.contains("-101")
        }
    }
}
