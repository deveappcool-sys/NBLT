package com.bililite.tv.ui.player

internal enum class VideoDecoderMode {
    AUTO_HARDWARE_FIRST,
    SOFTWARE_PREFERRED
}

internal enum class DecoderRecoveryAction {
    RETRY_SAME_QUALITY_WITH_HARDWARE,
    RETRY_SAME_QUALITY_WITH_SOFTWARE,
    LOWER_TO_QN64_WITH_HARDWARE,
    LOWER_TO_QN64_WITH_SOFTWARE,
    FAIL
}

internal data class DecoderRecoveryDecision(
    val action: DecoderRecoveryAction,
    val nextMode: VideoDecoderMode,
    val targetQn: Int
)

internal fun decideDecoderRecovery(
    currentMode: VideoDecoderMode,
    currentQn: Int,
    recoveryAttempt: Int,
    isStartupFailure: Boolean,
    preferLegacyAmlogicHardwareRecovery: Boolean
): DecoderRecoveryDecision {
    return when (currentMode) {
        VideoDecoderMode.AUTO_HARDWARE_FIRST -> when {
            preferLegacyAmlogicHardwareRecovery && currentQn > 64 -> {
                DecoderRecoveryDecision(
                    action = DecoderRecoveryAction.LOWER_TO_QN64_WITH_SOFTWARE,
                    nextMode = VideoDecoderMode.SOFTWARE_PREFERRED,
                    targetQn = 64
                )
            }

            else -> {
                DecoderRecoveryDecision(
                    action = DecoderRecoveryAction.RETRY_SAME_QUALITY_WITH_SOFTWARE,
                    nextMode = VideoDecoderMode.SOFTWARE_PREFERRED,
                    targetQn = currentQn
                )
            }
        }

        VideoDecoderMode.SOFTWARE_PREFERRED -> {
            if (currentQn > 64) {
                DecoderRecoveryDecision(
                    action = DecoderRecoveryAction.LOWER_TO_QN64_WITH_SOFTWARE,
                    nextMode = VideoDecoderMode.SOFTWARE_PREFERRED,
                    targetQn = 64
                )
            } else {
                DecoderRecoveryDecision(
                    action = DecoderRecoveryAction.FAIL,
                    nextMode = VideoDecoderMode.SOFTWARE_PREFERRED,
                    targetQn = currentQn
                )
            }
        }
    }
}

internal fun resolveDecoderRecoveryQn(sourceQn: Int?, fallbackQn: Int): Int {
    return sourceQn?.takeIf { it > 0 } ?: fallbackQn.coerceAtLeast(0)
}
