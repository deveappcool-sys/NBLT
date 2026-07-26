package com.nblt.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class DecoderRecoveryPolicyTest {
    @Test
    fun legacyAmlogicHighQualityFailureDropsDirectlyToQn64Software() {
        val decision = decideDecoderRecovery(
            currentMode = VideoDecoderMode.AUTO_HARDWARE_FIRST,
            currentQn = 80,
            recoveryAttempt = 1,
            isStartupFailure = true,
            preferLegacyAmlogicHardwareRecovery = true
        )

        assertEquals(DecoderRecoveryAction.LOWER_TO_QN64_WITH_SOFTWARE, decision.action)
        assertEquals(VideoDecoderMode.SOFTWARE_PREFERRED, decision.nextMode)
        assertEquals(64, decision.targetQn)
    }

    @Test
    fun nonLegacyStartupHardwareFailureKeepsSameQualitySoftwareFallback() {
        val decision = decideDecoderRecovery(
            currentMode = VideoDecoderMode.AUTO_HARDWARE_FIRST,
            currentQn = 80,
            recoveryAttempt = 1,
            isStartupFailure = true,
            preferLegacyAmlogicHardwareRecovery = false
        )

        assertEquals(DecoderRecoveryAction.RETRY_SAME_QUALITY_WITH_SOFTWARE, decision.action)
        assertEquals(VideoDecoderMode.SOFTWARE_PREFERRED, decision.nextMode)
        assertEquals(80, decision.targetQn)
    }

    @Test
    fun repeatedLegacyHardwareFailureStillUsesQn64Software() {
        val decision = decideDecoderRecovery(
            currentMode = VideoDecoderMode.AUTO_HARDWARE_FIRST,
            currentQn = 80,
            recoveryAttempt = 2,
            isStartupFailure = true,
            preferLegacyAmlogicHardwareRecovery = true
        )

        assertEquals(DecoderRecoveryAction.LOWER_TO_QN64_WITH_SOFTWARE, decision.action)
        assertEquals(VideoDecoderMode.SOFTWARE_PREFERRED, decision.nextMode)
        assertEquals(64, decision.targetQn)
    }

    @Test
    fun runtimeLegacyHardwareFailureUsesQn64Software() {
        val decision = decideDecoderRecovery(
            currentMode = VideoDecoderMode.AUTO_HARDWARE_FIRST,
            currentQn = 80,
            recoveryAttempt = 1,
            isStartupFailure = false,
            preferLegacyAmlogicHardwareRecovery = true
        )

        assertEquals(DecoderRecoveryAction.LOWER_TO_QN64_WITH_SOFTWARE, decision.action)
        assertEquals(VideoDecoderMode.SOFTWARE_PREFERRED, decision.nextMode)
        assertEquals(64, decision.targetQn)
    }

    @Test
    fun hardwareFailureAtQn64RetriesSoftwareAtQn64() {
        val decision = decideDecoderRecovery(
            currentMode = VideoDecoderMode.AUTO_HARDWARE_FIRST,
            currentQn = 64,
            recoveryAttempt = 3,
            isStartupFailure = true,
            preferLegacyAmlogicHardwareRecovery = true
        )

        assertEquals(DecoderRecoveryAction.RETRY_SAME_QUALITY_WITH_SOFTWARE, decision.action)
        assertEquals(VideoDecoderMode.SOFTWARE_PREFERRED, decision.nextMode)
        assertEquals(64, decision.targetQn)
    }

    @Test
    fun softwareFailureAtHighQualityLowersToQn64Software() {
        val decision = decideDecoderRecovery(
            currentMode = VideoDecoderMode.SOFTWARE_PREFERRED,
            currentQn = 80,
            recoveryAttempt = 2,
            isStartupFailure = true,
            preferLegacyAmlogicHardwareRecovery = true
        )

        assertEquals(DecoderRecoveryAction.LOWER_TO_QN64_WITH_SOFTWARE, decision.action)
        assertEquals(VideoDecoderMode.SOFTWARE_PREFERRED, decision.nextMode)
        assertEquals(64, decision.targetQn)
    }

    @Test
    fun exactDecoderSourceQnOverridesStaleScreenQuality() {
        assertEquals(
            64,
            resolveDecoderRecoveryQn(
                sourceQn = 64,
                fallbackQn = 80
            )
        )
    }

    @Test
    fun missingDecoderSourceQnUsesScreenFallback() {
        assertEquals(
            80,
            resolveDecoderRecoveryQn(
                sourceQn = null,
                fallbackQn = 80
            )
        )
    }

    @Test
    fun softwareFailureAtQn64StopsRecoveryChain() {
        val decision = decideDecoderRecovery(
            currentMode = VideoDecoderMode.SOFTWARE_PREFERRED,
            currentQn = 64,
            recoveryAttempt = 4,
            isStartupFailure = true,
            preferLegacyAmlogicHardwareRecovery = true
        )

        assertEquals(DecoderRecoveryAction.FAIL, decision.action)
        assertEquals(VideoDecoderMode.SOFTWARE_PREFERRED, decision.nextMode)
        assertEquals(64, decision.targetQn)
    }
}
