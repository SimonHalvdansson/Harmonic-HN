import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.dsl.AotMode
import org.jetbrains.compose.desktop.application.tasks.AbstractProguardTask
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.WriteProperties
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

val desktopVersionName = providers.gradleProperty("harmonic.versionName").get()
val desktopVersionCode = providers.gradleProperty("harmonic.versionCode").get()
val desktopOsName = System.getProperty("os.name").orEmpty().lowercase()
val isMacDesktopBuild = desktopOsName.contains("mac")
val isLinuxDesktopBuild = desktopOsName.startsWith("linux")
val isArm64DesktopBuild = System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")
val isWindowsArm64DesktopBuild = desktopOsName.startsWith("windows") &&
    System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    sourceSets {
        getByName("desktopMain").dependencies {
            implementation(project(":ui"))
            implementation(libs.compose.multiplatform.lifecycle.viewmodel.compose)
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.androidx.material3.adaptive)
            implementation(libs.androidx.material3.adaptive.layout)
            implementation(libs.androidx.navigationevent.compose.desktop)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.jna.platform)
            implementation(libs.jbr.api)
            implementation(when {
                isLinuxDesktopBuild && isArm64DesktopBuild -> libs.swt.gtk.arm64
                isLinuxDesktopBuild -> libs.swt.gtk.x64
                isWindowsArm64DesktopBuild -> libs.swt.win32.arm64
                else -> libs.swt.win32.x64
            })
        }
        getByName("desktopTest").dependencies {
            implementation(kotlin("test"))
        }
    }
}

val generateDesktopMetadata = tasks.register<WriteProperties>("generateDesktopMetadata") {
    description = "Generates the desktop application's version metadata."
    destinationFile = layout.buildDirectory
        .file("generated/desktopMetadata/harmonic-desktop.properties")
        .get()
        .asFile
    property("versionName", desktopVersionName)
    property("versionCode", desktopVersionCode)
}

val macWebViewSource = layout.projectDirectory.file("native/macos/HarmonicWebView.m")
val macWebViewOutput = layout.buildDirectory.file("macWebView/libharmonic-mac-webview.dylib")
val macWebViewResources = layout.buildDirectory.dir("generated/macWebViewResources")
val buildMacWebView = if (isMacDesktopBuild) {
    val outputFile = macWebViewOutput.get().asFile
    tasks.register<Exec>("buildMacWebView") {
        description = "Builds the native macOS WebView library."
        inputs.file(macWebViewSource)
        outputs.file(outputFile)
        doFirst { outputFile.parentFile.mkdirs() }
        commandLine(
            "/usr/bin/clang",
            "-fobjc-arc",
            "-fblocks",
            "-O2",
            "-dynamiclib",
            "-mmacosx-version-min=11.0",
            macWebViewSource.asFile.absolutePath,
            "-framework", "AppKit",
            "-framework", "WebKit",
            "-o", outputFile.absolutePath,
        )
    }
} else {
    null
}
val stageMacWebView = tasks.register<Sync>("stageMacWebView") {
    description = "Stages the native WebView library for macOS desktop builds."
    buildMacWebView?.let { nativeBuild -> dependsOn(nativeBuild) }
    if (isMacDesktopBuild) from(macWebViewOutput)
    into(macWebViewResources.map { it.dir("native") })
}

// Native browser fixtures are opt-in: ordinary desktop tests need neither AppKit nor a display.
tasks.withType<Test>().configureEach {
    if (isMacDesktopBuild && providers.systemProperty("harmonic.testMacWebView").orNull == "true") {
        dependsOn(stageMacWebView)
        classpath += files(macWebViewResources)
        systemProperty("harmonic.testMacWebView", "true")
        providers.systemProperty("harmonic.captchaFixtureHoldMs").orNull?.let {
            systemProperty("harmonic.captchaFixtureHoldMs", it)
        }
        providers.systemProperty("harmonic.captchaFixtureVerifyEscape").orNull?.let {
            systemProperty("harmonic.captchaFixtureVerifyEscape", it)
        }
        jvmArgs(
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
        )
    }
}

val macFoundationModelsOutput = layout.buildDirectory.file("macFoundationModels/harmonic-foundation-models")
val buildMacFoundationModels = if (isMacDesktopBuild) {
    tasks.register<Exec>("buildMacFoundationModels") {
        val source = layout.projectDirectory.file("native/macos/HarmonicFoundationModels.swift")
        val output = macFoundationModelsOutput.get().asFile
        inputs.file(source)
        outputs.file(output)
        doFirst { output.parentFile.mkdirs() }
        commandLine(
            "/usr/bin/xcrun", "swiftc", "-O", "-parse-as-library",
            "-target", "${if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "arm64" else "x86_64"}-apple-macosx11.0",
            source.asFile.absolutePath, "-o", output.absolutePath,
        )
    }
} else null

tasks.named<ProcessResources>("desktopProcessResources") {
    from(generateDesktopMetadata)
    from(rootProject.file("metadata/en-US/images/icon.png")) {
        rename { "harmonic-app-icon.png" }
    }
    from(project.file("icons/harmonic-macos.png")) {
        rename { "harmonic-app-icon-macos.png" }
    }
    from(project.file("icons/harmonic-windows.png")) {
        rename { "harmonic-app-icon-windows.png" }
    }
}

// Keep native resources off the unit-test classpath. Compose packaging and run include this
// separate JAR, while desktopTest can build the application JAR without building native host integrations.
val desktopNativeJar = tasks.register<Jar>("desktopNativeJar") {
    archiveFileName.set("harmonic-desktop-native.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    dependsOn(stageMacWebView)
    buildMacFoundationModels?.let { dependsOn(it) }
    if (isMacDesktopBuild) from(macFoundationModelsOutput) { into("native") }
    from(macWebViewResources)
}

val desktopProjectJars = files(
    desktopNativeJar.flatMap { it.archiveFile },
    layout.buildDirectory.file("libs/desktop_app-desktop.jar"),
    project(":ui").layout.buildDirectory.file("libs/ui-desktop.jar"),
    project(":core").layout.buildDirectory.file("libs/core-desktop.jar"),
    project(":resources").layout.buildDirectory.file("libs/resources-desktop.jar"),
).builtBy(
    ":desktop_app:desktopJar",
    ":ui:desktopJar",
    ":core:desktopJar",
    ":resources:desktopJar",
)
// The Compose run task normally launches directly from build/libs. Other Gradle builds can
// replace those jars while the desktop JVM is still running, making later class loads fail.
// Launch from a private snapshot so tests and Android builds cannot invalidate the open app.
val desktopRunClasspathDirectory = layout.buildDirectory.dir("desktopRunClasspath")
val prepareDesktopRunClasspath = tasks.register<Sync>("prepareDesktopRunClasspath") {
    description = "Copies desktop project jars into a stable runtime classpath."
    from(desktopProjectJars)
    into(desktopRunClasspathDirectory)
}
val stableDesktopProjectJars = files(
    desktopRunClasspathDirectory.map { directory ->
        directory.asFileTree.matching { include("*.jar") }
    },
).builtBy(prepareDesktopRunClasspath)

compose.desktop {
    application {
        // Compose's default target configuration ignores fromFiles; supply the runtime explicitly
        // so the native JAR is packaged without adding it to desktopTest's dependencies.
        disableDefaultConfiguration()
        mainClass = "com.simon.harmonichackernews.desktop.DesktopAppMainKt"
        mainJar.set(tasks.named<Jar>("desktopJar").flatMap { it.archiveFile })
        fromFiles(desktopProjectJars, configurations.named("desktopRuntimeClasspath"))
        providers.gradleProperty("harmonicDesktopJavaHome").orNull?.let { javaHome = it }
        buildTypes.release.aot {
            mode = if (providers.gradleProperty("harmonicDesktopAppCds")
                    // Windows ARM64 JDK 21 archives retain build-directory JAR paths.
                    // Keep installed apps independent of the build machine.
                    .map(String::toBoolean).getOrElse(!isWindowsArm64DesktopBuild)) {
                AotMode.AppCdsPrebuild
            } else {
                AotMode.None
            }
        }
        if (isMacDesktopBuild) {
            // These must be present before AWT initializes. They give development runs Harmonic's
            // menu-bar/display name where the JBR supports it, but only a packaged .app has a true
            // macOS bundle identity instead of the underlying java launcher.
            jvmArgs += listOf(
                "-Xdock:name=Harmonic",
                "-Dapple.awt.application.name=Harmonic",
                "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            )
        }
        buildTypes.release.proguard {
            isEnabled.set(true)
            version.set("7.10.0")
            // Push/PR installers still shrink and verify the release JARs, but can skip
            // expensive bytecode optimization. Tagged and local releases default to it.
            optimize.set(
                providers.gradleProperty("harmonicDesktopOptimize")
                    .map(String::toBoolean)
                    .orElse(true),
            )
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
        nativeDistributions {
            // Protobuf's lite runtime loads sun.misc.Unsafe reflectively for DataStore writes.
            modules("jdk.unsupported")
            if (desktopOsName.startsWith("windows")) modules("jdk.accessibility")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Harmonic"
            packageVersion = desktopVersionName
            description = "A desktop Hacker News client powered by Harmonic's shared Kotlin app"
            vendor = "Simon Halvdansson"
            macOS {
                iconFile.set(project.layout.projectDirectory.file("icons/harmonic.icns"))
                bundleID = "com.simon.harmonichackernews.desktop"
            }
            windows {
                // MSI requires three version components, even for releases named MAJOR.MINOR.
                packageVersion = if (desktopVersionName.count { it == '.' } == 1) {
                    "$desktopVersionName.0"
                } else {
                    desktopVersionName
                }
                iconFile.set(project.layout.projectDirectory.file("icons/harmonic.ico"))
            }
            linux {
                iconFile.set(
                    rootProject.layout.projectDirectory.file(
                        "metadata/en-US/images/icon.png",
                    ),
                )
            }
        }
    }
}

tasks.withType<AbstractJPackageTask>().configureEach {
    if (targetFormat == TargetFormat.Deb) {
        // SWT loads these libraries at runtime, so jpackage's ELF dependency scan cannot see them.
        // Compose writes freeArgs verbatim to jpackage's @argfile; quote the whole dependency expression.
        freeArgs.addAll("--linux-package-deps", "\"libgtk-3-0 | libgtk-3-0t64, libwebkit2gtk-4.1-0, libx11-6\"")
    }
}

afterEvaluate {
    tasks.named<JavaExec>("run") {
        val mutableProjectClasspath = classpath
        classpath = mutableProjectClasspath
            .minus(desktopProjectJars)
            .plus(stableDesktopProjectJars)
        systemProperty("harmonic.desktop.debug", "true")
    }
}

tasks.withType<AbstractProguardTask>().configureEach {
    // ProGuard copies SWT's signing metadata even though it rewrites the signed classes.
    // Remove invalid JAR signatures from processed outputs before jpackage consumes them.
    doLast {
        val outputDirectory = (this as AbstractProguardTask).destinationDir.get().asFile
        val signature = Regex("META-INF/[^/]+\\.(SF|RSA|DSA|EC)", RegexOption.IGNORE_CASE)
        outputDirectory.listFiles().orEmpty().filter { it.extension == "jar" }.forEach { jar ->
            ZipFile(jar).use { input ->
                val entries = input.entries().asSequence().toList()
                if (entries.none { signature.matches(it.name) }) return@forEach
                val unsignedJar = temporaryDir.resolve(jar.name)
                ZipOutputStream(unsignedJar.outputStream().buffered()).use { output ->
                    entries.filterNot { signature.matches(it.name) }.forEach { entry ->
                        output.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                        input.getInputStream(entry).use { it.copyTo(output) }
                        output.closeEntry()
                    }
                }
            }
            Files.move(temporaryDir.resolve(jar.name).toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
