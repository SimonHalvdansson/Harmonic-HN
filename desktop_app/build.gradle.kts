import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractProguardTask
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.WriteProperties
import org.gradle.language.jvm.tasks.ProcessResources
import java.util.Properties
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

val desktopVersionName = providers.gradleProperty("harmonic.versionName").get()
val desktopVersionCode = providers.gradleProperty("harmonic.versionCode").get()
val desktopOsName = System.getProperty("os.name").orEmpty().lowercase()
val isMacDesktopBuild = desktopOsName.contains("mac")
val macDesktopCompilerArguments = if (isMacDesktopBuild) {
    listOf(
        "-DCMAKE_C_COMPILER=/usr/bin/clang",
        "-DCMAKE_CXX_COMPILER=/usr/bin/clang++",
    )
} else {
    emptyList()
}

val cmakeExecutable = providers.provider {
    providers.gradleProperty("harmonic.cmakeExecutable").orNull
        ?: providers.environmentVariable("CMAKE_EXECUTABLE").orNull
        ?: run {
            val executableName = if (System.getProperty("os.name").startsWith("Windows")) {
                "cmake.exe"
            } else {
                "cmake"
            }
            System.getenv("PATH").orEmpty()
                .split(File.pathSeparatorChar)
                .asSequence()
                .map { File(it, executableName) }
                .firstOrNull(File::isFile)
                ?.absolutePath
                ?: run {
                    val properties = Properties()
                    rootProject.file("local.properties").takeIf(File::isFile)?.inputStream()?.use {
                        properties.load(it)
                    }
                    val sdk = properties.getProperty("sdk.dir")
                        ?: System.getenv("ANDROID_SDK_ROOT")
                        ?: System.getenv("ANDROID_HOME")
                    sdk?.let(::File)?.resolve("cmake")
                        ?.listFiles()
                        ?.filter(File::isDirectory)
                        ?.maxByOrNull(File::getName)
                        ?.resolve("bin/$executableName")
                        ?.takeIf(File::isFile)
                        ?.absolutePath
                }
                ?: "cmake"
        }
}

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
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.androidx.material3.adaptive)
            implementation(libs.androidx.material3.adaptive.layout)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(compose.desktop.currentOs)
            implementation(libs.jna.platform)
            implementation(libs.swt.win32)
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

val desktopLocalAiSource = rootProject.layout.projectDirectory.dir("local_ai_runtime/src/main/cpp")
val desktopLocalAiBuild = layout.buildDirectory.dir("desktopLocalAi/cmake")
val desktopLocalAiOutput = layout.buildDirectory.dir("desktopLocalAi/output")
val desktopLocalAiResources = layout.buildDirectory.dir("generated/desktopLocalAiResources")
val desktopLocalAiLibrary = desktopLocalAiOutput.map {
    it.file(System.mapLibraryName("harmonic-local-ai"))
}
// CI sets this only after an exact native-source/toolchain cache hit.
val prebuiltDesktopLocalAi = providers.gradleProperty("harmonicDesktopLocalAiPrebuilt")
    .map { File(it, System.mapLibraryName("harmonic-local-ai")) }
val desktopLocalAiSources = fileTree(desktopLocalAiSource) {
    include("**/*.c", "**/*.cc", "**/*.cpp", "**/*.h", "**/*.hpp", "**/CMakeLists.txt")
}

val configureDesktopLocalAi = tasks.register<Exec>("configureDesktopLocalAi") {
    description = "Configures CMake for the desktop local AI library."
    inputs.files(desktopLocalAiSources)
    outputs.file(desktopLocalAiBuild.map { it.file("CMakeCache.txt") })
    val source = desktopLocalAiSource.asFile.absolutePath
    val build = desktopLocalAiBuild.get().asFile.absolutePath
    val output = desktopLocalAiOutput.get().asFile.absolutePath
    commandLine(
        buildList {
            add(cmakeExecutable.get())
            addAll(listOf("-S", source, "-B", build))
            addAll(macDesktopCompilerArguments)
            addAll(
                listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DHARMONIC_BUILD_DESKTOP=ON",
                    "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=$output",
                    "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=$output",
                    "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE=$output",
                    "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE=$output",
                ),
            )
        },
    )
}

val buildDesktopLocalAi = tasks.register<Exec>("buildDesktopLocalAi") {
    description = "Builds the desktop local AI shared library."
    dependsOn(configureDesktopLocalAi)
    inputs.files(desktopLocalAiSources)
    outputs.file(desktopLocalAiLibrary)
    commandLine(
        cmakeExecutable.get(),
        "--build", desktopLocalAiBuild.get().asFile.absolutePath,
        "--config", "Release",
        "--target", "harmonic-local-ai",
        "--parallel", "2",
    )
}

val stageDesktopLocalAi = tasks.register<Sync>("stageDesktopLocalAi") {
    description = "Stages the desktop local AI library as a packaged resource."
    if (prebuiltDesktopLocalAi.isPresent) {
        val library = prebuiltDesktopLocalAi.get()
        check(library.isFile) { "Missing cached native library: $library" }
        from(library)
    } else {
        dependsOn(buildDesktopLocalAi)
        from(desktopLocalAiLibrary)
    }
    into(desktopLocalAiResources.map { it.dir("native") })
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
    from(rootProject.file("fastlane/metadata/android/en-US/images/icon.png")) {
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
// separate JAR, while desktopTest can build the application JAR without invoking a C++ compiler.
val desktopNativeJar = tasks.register<Jar>("desktopNativeJar") {
    archiveFileName.set("harmonic-desktop-native.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    dependsOn(stageDesktopLocalAi, stageMacWebView)
    buildMacFoundationModels?.let { dependsOn(it) }
    if (isMacDesktopBuild) from(macFoundationModelsOutput) { into("native") }
    from(desktopLocalAiResources)
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
            optimize.set(true)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
        nativeDistributions {
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
                iconFile.set(project.layout.projectDirectory.file("icons/harmonic.ico"))
            }
            linux {
                iconFile.set(
                    rootProject.layout.projectDirectory.file(
                        "fastlane/metadata/android/en-US/images/icon.png",
                    ),
                )
            }
        }
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
