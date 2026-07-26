package com.nblt.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilityClassifierTest {
    @Test
    fun tx3LikeAmlogicAndroid9IsLegacyAmlogic() {
        val result = DeviceCapabilityClassifier.classify(
            baseInput(
                manufacturer = "Amlogic",
                model = "TX3",
                device = "u211",
                board = "u211",
                hardware = "amlogic",
                sdkInt = 28,
                securityPatchYear = 2018,
                is64BitRuntime = false,
                totalRamMb = 3872,
                memoryClassMb = 256
            )
        )

        assertEquals(PlaybackDeviceClass.LEGACY_AMLOGIC, result.deviceClass)
        assertTrue(result.reasons.contains("amlogic_platform"))
        assertTrue(result.reasons.contains("32_bit_runtime"))
    }

    @Test
    fun lowRamTelevisionIsLowEnd() {
        val result = DeviceCapabilityClassifier.classify(
            baseInput(
                manufacturer = "Generic",
                sdkInt = 30,
                isLowRam = true,
                totalRamMb = 1024,
                memoryClassMb = 128
            )
        )

        assertEquals(PlaybackDeviceClass.LOW_END_TV, result.deviceClass)
    }

    @Test
    fun modern64Bit4kTelevisionIsHighCapability() {
        val result = DeviceCapabilityClassifier.classify(
            baseInput(
                manufacturer = "ModernTV",
                sdkInt = 33,
                securityPatchYear = 2025,
                is64BitRuntime = true,
                totalRamMb = 4096,
                memoryClassMb = 384,
                hasHardwareAvc = true,
                hasHardwareHevc4k = true,
                hasHardwareVp9 = true
            )
        )

        assertEquals(PlaybackDeviceClass.HIGH_CAPABILITY_TV, result.deviceClass)
    }

    @Test
    fun normalTelevisionWithHardwareAvcIsStandard() {
        val result = DeviceCapabilityClassifier.classify(
            baseInput(
                manufacturer = "StandardTV",
                sdkInt = 29,
                securityPatchYear = 2023,
                is64BitRuntime = true,
                totalRamMb = 2048,
                memoryClassMb = 192,
                hasHardwareAvc = true
            )
        )

        assertEquals(PlaybackDeviceClass.STANDARD_TV, result.deviceClass)
    }

    @Test
    fun deviceWithoutTvOrCodecEvidenceUsesSafeUnknownProfile() {
        val result = DeviceCapabilityClassifier.classify(
            baseInput(
                isTv = false,
                hasHardwareAvc = false,
                totalRamMb = 2048,
                memoryClassMb = 192
            )
        )

        assertEquals(PlaybackDeviceClass.UNKNOWN_SAFE, result.deviceClass)
    }

    private fun baseInput(
        manufacturer: String = "Unknown",
        brand: String = "Unknown",
        model: String = "Unknown",
        device: String = "unknown",
        board: String = "unknown",
        hardware: String = "unknown",
        sdkInt: Int = 30,
        securityPatchYear: Int? = 2024,
        isTv: Boolean = true,
        isLowRam: Boolean = false,
        totalRamMb: Int = 2048,
        memoryClassMb: Int = 192,
        is64BitRuntime: Boolean = true,
        hasHardwareAvc: Boolean = false,
        hasHardwareHevc4k: Boolean = false,
        hasHardwareVp9: Boolean = false,
        hasHardwareAv1: Boolean = false
    ): DeviceClassificationInput {
        return DeviceClassificationInput(
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            device = device,
            board = board,
            hardware = hardware,
            sdkInt = sdkInt,
            securityPatchYear = securityPatchYear,
            isTv = isTv,
            isLowRam = isLowRam,
            totalRamMb = totalRamMb,
            memoryClassMb = memoryClassMb,
            is64BitRuntime = is64BitRuntime,
            hasHardwareAvc = hasHardwareAvc,
            hasHardwareHevc4k = hasHardwareHevc4k,
            hasHardwareVp9 = hasHardwareVp9,
            hasHardwareAv1 = hasHardwareAv1
        )
    }
}
