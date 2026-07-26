# Player recovery classifies a few Media3 failures by class name.
# Keep those names stable when R8 obfuscation is enabled.
-keepnames class androidx.media3.exoplayer.ExoTimeoutException
-keepnames class androidx.media3.common.ParserException
-keepnames class androidx.media3.exoplayer.video.MediaCodecVideoDecoderException

# P7-04 release log stripping
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
