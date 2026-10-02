# JNA uses JNI and reflection for native functions, callbacks, and structure fields.
-keep class com.sun.jna.** { *; }
-keep interface * extends com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable

# Enum.valueOf reads values() reflectively (for example Haze's SurfaceProfile in Settings).
# Keep these methods public: ProGuard 7.10 can privatize them with allowoptimization.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# SWT's Windows browser dispatches through JNI and reflective widget/browser lookups.
-keep class org.eclipse.swt.** { *; }

# Compose's ProGuard task does not import dependencies' META-INF/proguard rules.
# Preserve Coil's service-loaded image fetchers/decoders and Ktor's engine providers.
-keep class coil3.util.DecoderServiceLoaderTarget { *; }
-keep class coil3.util.FetcherServiceLoaderTarget { *; }
-keep class coil3.util.ServiceLoaderComponentRegistry { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }
-keep class * implements io.ktor.client.HttpClientEngineContainer { *; }
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}

# Some JBR distributions include unused JOGL/SWT adapters in their JDK modules.
# They reference the packaged SWT classes, but Harmonic does not use those adapters.
-dontwarn com.jogamp.newt.swt.**
-dontwarn com.jogamp.opengl.swt.**
-dontwarn jogamp.newt.swt.event.**

# LaTeX 1.5.6 contains an unused private inline companion method referencing its
# owner's private field. Its actual tokenizer has the inlined, valid field access.
-dontwarn com.hrm.latex.parser.tokenizer.LatexTokenizer$Companion
# Image export targets an older Skia signature; Harmonic only renders inline math.
-dontwarn com.hrm.latex.renderer.export.LatexExporter_jvmKt

# LiteRT-LM invokes its Kotlin/JVM bridge and message callbacks from native code.
-keep class com.google.ai.edge.litertlm.** { *; }
# Its JSON bridge uses Gson's reflective generic type tokens.
-keep class * extends com.google.gson.reflect.TypeToken

# OkHttp probes optional TLS providers; desktop uses the bundled JBR's TLS provider.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**
# Graal native-image integration is unused in the packaged JVM application.
-dontwarn okhttp3.internal.graal.**
