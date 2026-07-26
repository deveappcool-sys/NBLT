package com.bililite.tv.ui.player

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.os.Process
import android.util.Log
import android.view.Display
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG_DEVICE_PROFILE = "BiliDeviceProfile"
private const val TAG_CODEC_CAPABILITY = "BiliCodecCapability"
private const val BYTES_PER_MIB = 1024L * 1024L

internal object DeviceCapabilityProfiler {
    fun isLegacyAmlogicRuntime(): Boolean {
        val identity = listOf(
            Build.MANUFACTURER,
            Build.BRAND,
            Build.MODEL,
            Build.DEVICE,
            Build.BOARD,
            Build.HARDWARE
        ).joinToString(" ").lowercase(Locale.US)
        val looksAmlogic = identity.contains("amlogic") ||
            identity.contains("u211") ||
            identity.contains("meson")
        val securityPatchYear = Build.VERSION.SECURITY_PATCH
            .takeIf { it.length >= 4 }
            ?.take(4)
            ?.toIntOrNull()
        val staleSecurityPatch = securityPatchYear?.let { it <= 2019 } == true
        return looksAmlogic && (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ||
                !Process.is64Bit() ||
                staleSecurityPatch
            )
    }

    @Volatile
    private var cachedProfile: DeviceCapabilityProfile? = null
    private val logged = AtomicBoolean(false)
    private val logScheduled = AtomicBoolean(false)

    fun get(context: Context): DeviceCapabilityProfile {
        cachedProfile?.let { return it }
        return synchronized(this) {
            cachedProfile ?: detect(context.applicationContext).also { cachedProfile = it }
        }
    }

    fun scheduleLog(context: Context, reason: String) {
        if (!logScheduled.compareAndSet(false, true)) {
            return
        }
        val appContext = context.applicationContext
        Thread(
            { logOnce(appContext, reason) },
            "BiliDeviceCapability"
        ).apply { isDaemon = true }.start()
    }

    fun logOnce(context: Context, reason: String) {
        val profile = get(context)
        if (!logged.compareAndSet(false, true)) {
            return
        }

        Log.i(
            TAG_DEVICE_PROFILE,
            "capability profile reason=$reason, observationalOnly=true, ${profile.compactSummary()}"
        )
        Log.i(
            TAG_DEVICE_PROFILE,
            "build brand=${profile.brand}, board=${profile.board}, hardware=${profile.hardware}, " +
                "fingerprint=${profile.fingerprint}, securityPatch=${profile.securityPatch}, " +
                "isTv=${profile.isTv}, supports64BitAbi=${profile.supports64BitAbi}, " +
                "is64BitRuntime=${profile.is64BitRuntime}"
        )
        Log.i(
            TAG_DEVICE_PROFILE,
            "memory totalRamMb=${profile.totalRamMb}, memoryClassMb=${profile.memoryClassMb}, " +
                "largeMemoryClassMb=${profile.largeMemoryClassMb}, heapMaxMb=${profile.heapMaxMb}, " +
                "lowRam=${profile.isLowRam}"
        )
        Log.i(
            TAG_DEVICE_PROFILE,
            "display width=${profile.display.width}, height=${profile.display.height}, " +
                "refreshRateHz=${formatFloat(profile.display.refreshRateHz)}, " +
                "hdrTypes=${profile.display.hdrTypes.joinToString("|")}, is4k=${profile.display.is4k}"
        )

        val targetMimes = listOf(
            "video/avc",
            "video/hevc",
            "video/x-vnd.on2.vp9",
            "video/av01"
        )
        targetMimes.forEach { mimeType ->
            val decoder = profile.bestDecoder(mimeType)
            Log.i(
                TAG_DEVICE_PROFILE,
                "codec summary mime=$mimeType, decoder=${decoder?.name.orEmpty()}, " +
                    "hardware=${decoder?.hardwareAccelerated ?: false}, " +
                    "max=${decoder?.maxWidth ?: 0}x${decoder?.maxHeight ?: 0}@${decoder?.maxFrameRate ?: 0}, " +
                    "adaptive=${decoder?.adaptivePlayback ?: false}, secure=${decoder?.securePlayback ?: false}"
            )
        }

        profile.videoDecoders.forEach { decoder ->
            Log.i(
                TAG_CODEC_CAPABILITY,
                "decoder name=${decoder.name}, mime=${decoder.mimeType}, " +
                    "hardware=${decoder.hardwareAccelerated}, softwareOnly=${decoder.softwareOnly}, " +
                    "vendor=${decoder.vendor}, adaptive=${decoder.adaptivePlayback}, " +
                    "secure=${decoder.securePlayback}, tunneled=${decoder.tunneledPlayback}, " +
                    "max=${decoder.maxWidth}x${decoder.maxHeight}@${decoder.maxFrameRate}, " +
                    "profileLevels=${decoder.profileLevelCount}"
            )
        }
    }

    private fun detect(context: Context): DeviceCapabilityProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val totalRamMb = (memoryInfo.totalMem / BYTES_PER_MIB).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val supportedAbis = Build.SUPPORTED_ABIS.orEmpty().toList()
        val supports64BitAbi = supportedAbis.any { it.contains("64") }
        val is64BitRuntime = Process.is64Bit()
        val display = detectDisplay(context)
        val decoders = detectVideoDecoders()
        val isTv = isTelevision(context)
        val securityPatchYear = Build.VERSION.SECURITY_PATCH
            .takeIf { it.length >= 4 }
            ?.take(4)
            ?.toIntOrNull()

        val classification = DeviceCapabilityClassifier.classify(
            DeviceClassificationInput(
                manufacturer = Build.MANUFACTURER.orEmpty(),
                brand = Build.BRAND.orEmpty(),
                model = Build.MODEL.orEmpty(),
                device = Build.DEVICE.orEmpty(),
                board = Build.BOARD.orEmpty(),
                hardware = Build.HARDWARE.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT,
                securityPatchYear = securityPatchYear,
                isTv = isTv,
                isLowRam = activityManager.isLowRamDevice,
                totalRamMb = totalRamMb,
                memoryClassMb = activityManager.memoryClass,
                is64BitRuntime = is64BitRuntime,
                hasHardwareAvc = decoders.hasHardwareDecoder("video/avc"),
                hasHardwareHevc4k = decoders.hasHardwareDecoder("video/hevc", 3840, 2160),
                hasHardwareVp9 = decoders.hasHardwareDecoder("video/x-vnd.on2.vp9"),
                hasHardwareAv1 = decoders.hasHardwareDecoder("video/av01")
            )
        )

        return DeviceCapabilityProfile(
            deviceClass = classification.deviceClass,
            classificationReasons = classification.reasons,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            board = Build.BOARD.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            fingerprint = Build.FINGERPRINT.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty(),
            supportedAbis = supportedAbis,
            supports64BitAbi = supports64BitAbi,
            is64BitRuntime = is64BitRuntime,
            isTv = isTv,
            isLowRam = activityManager.isLowRamDevice,
            totalRamMb = totalRamMb,
            memoryClassMb = activityManager.memoryClass,
            largeMemoryClassMb = activityManager.largeMemoryClass,
            heapMaxMb = (Runtime.getRuntime().maxMemory() / BYTES_PER_MIB)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt(),
            display = display,
            videoDecoders = decoders
        )
    }

    private fun detectDisplay(context: Context): DisplayCapability {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        val hdrTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            display?.hdrCapabilities?.supportedHdrTypes?.toList().orEmpty()
        } else {
            emptyList()
        }
        return DisplayCapability(
            width = mode?.physicalWidth ?: 0,
            height = mode?.physicalHeight ?: 0,
            refreshRateHz = mode?.refreshRate ?: display?.refreshRate ?: 0f,
            hdrTypes = hdrTypes
        )
    }

    private fun isTelevision(context: Context): Boolean {
        val uiModeType = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val packageManager = context.packageManager
        return uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
    }

    private fun detectVideoDecoders(): List<VideoDecoderCapability> {
        val targetMimes = setOf(
            "video/avc",
            "video/hevc",
            "video/x-vnd.on2.vp9",
            "video/av01",
            "video/dolby-vision"
        )
        val codecInfos = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.toList()
        }.onFailure { error ->
            Log.e(TAG_DEVICE_PROFILE, "codec enumeration failed: ${error.message}", error)
        }.getOrDefault(emptyList())

        return codecInfos
            .asSequence()
            .filterNot { it.isEncoder }
            .flatMap { codecInfo ->
                codecInfo.supportedTypes
                    .asSequence()
                    .map { it.lowercase(Locale.US) }
                    .filter { it in targetMimes }
                    .mapNotNull { mimeType -> codecInfo.toVideoCapability(mimeType) }
            }
            .distinctBy { "${it.name}|${it.mimeType}" }
            .sortedWith(compareBy<VideoDecoderCapability> { it.mimeType }.thenBy { it.name })
            .toList()
    }
}

private fun MediaCodecInfo.toVideoCapability(mimeType: String): VideoDecoderCapability? {
    return runCatching {
        val capabilities = getCapabilitiesForType(mimeType)
        val videoCapabilities = capabilities.videoCapabilities
        val softwareOnly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isSoftwareOnly
        } else {
            name.isLikelySoftwareCodec()
        }
        val hardwareAccelerated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isHardwareAccelerated
        } else {
            !softwareOnly
        }
        val vendor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isVendor
        } else {
            !name.startsWith("OMX.google.", ignoreCase = true) &&
                !name.startsWith("c2.android.", ignoreCase = true) &&
                !name.startsWith("c2.google.", ignoreCase = true)
        }

        VideoDecoderCapability(
            name = name,
            mimeType = mimeType,
            hardwareAccelerated = hardwareAccelerated,
            softwareOnly = softwareOnly,
            vendor = vendor,
            adaptivePlayback = capabilities.isFeatureSupported(
                MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback
            ),
            securePlayback = capabilities.isFeatureSupported(
                MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback
            ),
            tunneledPlayback = capabilities.isFeatureSupported(
                MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback
            ),
            maxWidth = videoCapabilities?.supportedWidths?.upper ?: 0,
            maxHeight = videoCapabilities?.supportedHeights?.upper ?: 0,
            maxFrameRate = videoCapabilities?.supportedFrameRates?.upper?.toInt() ?: 0,
            profileLevelCount = capabilities.profileLevels.orEmpty().size
        )
    }.onFailure { error ->
        Log.w(
            TAG_DEVICE_PROFILE,
            "codec capability read failed name=$name, mime=$mimeType, error=${error.message}"
        )
    }.getOrNull()
}

private fun String.isLikelySoftwareCodec(): Boolean {
    val normalized = lowercase(Locale.US)
    return normalized.startsWith("omx.google.") ||
        normalized.startsWith("omx.ffmpeg.") ||
        normalized.startsWith("c2.android.") ||
        normalized.startsWith("c2.google.") ||
        normalized.contains("software") ||
        normalized.contains(".sw.")
}

private fun List<VideoDecoderCapability>.hasHardwareDecoder(
    mimeType: String,
    minWidth: Int = 1,
    minHeight: Int = 1
): Boolean {
    return any { decoder ->
        decoder.mimeType.equals(mimeType, ignoreCase = true) &&
            decoder.hardwareAccelerated &&
            decoder.supportsAtLeast(minWidth, minHeight)
    }
}

private fun formatFloat(value: Float): String {
    return String.format(Locale.US, "%.2f", value)
}
