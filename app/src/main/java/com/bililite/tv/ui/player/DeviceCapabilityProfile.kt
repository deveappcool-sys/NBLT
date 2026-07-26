package com.bililite.tv.ui.player

internal enum class PlaybackDeviceClass {
    LEGACY_AMLOGIC,
    LOW_END_TV,
    STANDARD_TV,
    HIGH_CAPABILITY_TV,
    UNKNOWN_SAFE
}

internal data class DeviceClassificationInput(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val board: String,
    val hardware: String,
    val sdkInt: Int,
    val securityPatchYear: Int?,
    val isTv: Boolean,
    val isLowRam: Boolean,
    val totalRamMb: Int,
    val memoryClassMb: Int,
    val is64BitRuntime: Boolean,
    val hasHardwareAvc: Boolean,
    val hasHardwareHevc4k: Boolean,
    val hasHardwareVp9: Boolean,
    val hasHardwareAv1: Boolean
)

internal data class DeviceClassification(
    val deviceClass: PlaybackDeviceClass,
    val reasons: List<String>
)

internal object DeviceCapabilityClassifier {
    fun classify(input: DeviceClassificationInput): DeviceClassification {
        val identity = listOf(
            input.manufacturer,
            input.brand,
            input.model,
            input.device,
            input.board,
            input.hardware
        ).joinToString(" ").lowercase()

        val looksAmlogic = identity.contains("amlogic") ||
            identity.contains("u211") ||
            identity.contains("meson")
        val staleSecurityPatch = input.securityPatchYear?.let { it <= 2019 } == true
        val legacyAmlogic = looksAmlogic && (
            input.sdkInt <= 28 ||
                !input.is64BitRuntime ||
                staleSecurityPatch
            )

        if (legacyAmlogic) {
            return DeviceClassification(
                deviceClass = PlaybackDeviceClass.LEGACY_AMLOGIC,
                reasons = buildList {
                    add("amlogic_platform")
                    if (input.sdkInt <= 28) add("api_${input.sdkInt}")
                    if (!input.is64BitRuntime) add("32_bit_runtime")
                    if (staleSecurityPatch) add("legacy_security_patch")
                }
            )
        }

        val lowMemory = input.isLowRam ||
            input.memoryClassMb in 1..128 ||
            input.totalRamMb in 1..1535
        if (lowMemory) {
            return DeviceClassification(
                deviceClass = PlaybackDeviceClass.LOW_END_TV,
                reasons = buildList {
                    if (input.isLowRam) add("android_low_ram")
                    if (input.memoryClassMb in 1..128) add("memory_class_${input.memoryClassMb}mb")
                    if (input.totalRamMb in 1..1535) add("ram_${input.totalRamMb}mb")
                }
            )
        }

        val highCapability = input.isTv &&
            input.sdkInt >= 29 &&
            input.is64BitRuntime &&
            input.memoryClassMb >= 256 &&
            input.hasHardwareAvc &&
            input.hasHardwareHevc4k &&
            (input.hasHardwareVp9 || input.hasHardwareAv1)
        if (highCapability) {
            return DeviceClassification(
                deviceClass = PlaybackDeviceClass.HIGH_CAPABILITY_TV,
                reasons = listOf(
                    "modern_tv",
                    "64_bit_runtime",
                    "hardware_4k_codec",
                    "memory_class_${input.memoryClassMb}mb"
                )
            )
        }

        if (input.isTv || input.hasHardwareAvc) {
            return DeviceClassification(
                deviceClass = PlaybackDeviceClass.STANDARD_TV,
                reasons = buildList {
                    if (input.isTv) add("television_ui")
                    if (input.hasHardwareAvc) add("hardware_avc")
                    if (input.hasHardwareHevc4k) add("hardware_hevc_4k")
                }
            )
        }

        return DeviceClassification(
            deviceClass = PlaybackDeviceClass.UNKNOWN_SAFE,
            reasons = listOf("insufficient_capability_evidence")
        )
    }
}

internal data class DisplayCapability(
    val width: Int,
    val height: Int,
    val refreshRateHz: Float,
    val hdrTypes: List<Int>
) {
    val is4k: Boolean
        get() = width >= 3840 && height >= 2160
}

internal data class VideoDecoderCapability(
    val name: String,
    val mimeType: String,
    val hardwareAccelerated: Boolean,
    val softwareOnly: Boolean,
    val vendor: Boolean,
    val adaptivePlayback: Boolean,
    val securePlayback: Boolean,
    val tunneledPlayback: Boolean,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxFrameRate: Int,
    val profileLevelCount: Int
) {
    fun supportsAtLeast(width: Int, height: Int): Boolean {
        return maxWidth >= width && maxHeight >= height
    }
}

internal data class DeviceCapabilityProfile(
    val deviceClass: PlaybackDeviceClass,
    val classificationReasons: List<String>,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val board: String,
    val hardware: String,
    val fingerprint: String,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val supportedAbis: List<String>,
    val supports64BitAbi: Boolean,
    val is64BitRuntime: Boolean,
    val isTv: Boolean,
    val isLowRam: Boolean,
    val totalRamMb: Int,
    val memoryClassMb: Int,
    val largeMemoryClassMb: Int,
    val heapMaxMb: Int,
    val display: DisplayCapability,
    val videoDecoders: List<VideoDecoderCapability>
) {
    fun bestDecoder(mimeType: String): VideoDecoderCapability? {
        return videoDecoders
            .asSequence()
            .filter { it.mimeType.equals(mimeType, ignoreCase = true) }
            .sortedWith(
                compareByDescending<VideoDecoderCapability> { it.hardwareAccelerated }
                    .thenByDescending { it.maxWidth.toLong() * it.maxHeight.toLong() }
                    .thenByDescending { it.maxFrameRate }
            )
            .firstOrNull()
    }

    fun compactSummary(): String {
        return "class=$deviceClass, reasons=${classificationReasons.joinToString("|")}, " +
            "device=$manufacturer/$model/$device, api=$sdkInt/$androidRelease, " +
            "abis=${supportedAbis.joinToString("|")}, ram=${totalRamMb}MB, " +
            "memoryClass=${memoryClassMb}MB, lowRam=$isLowRam, " +
            "display=${display.width}x${display.height}@${"%.2f".format(display.refreshRateHz)}Hz"
    }
}
