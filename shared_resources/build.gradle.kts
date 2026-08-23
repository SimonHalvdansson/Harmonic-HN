import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

private object AdblockResourceFormat {
    const val PATH = "files/adblock/adblockserverlist.bin"
    const val MAGIC = 0x48414431
}

@CacheableTask
abstract class GenerateAdblocklistTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputRoot = outputDirectory.get().asFile.toPath()
        outputRoot.toFile().deleteRecursively()

        val hashes = Files.newBufferedReader(
            sourceFile.get().asFile.toPath(),
            StandardCharsets.UTF_8,
        ).useLines { lines ->
            lines.map { host ->
                var hash = -3_750_763_034_362_895_579L
                host.forEach { character ->
                    hash = hash xor character.code.toLong()
                    hash *= 1_099_511_628_211L
                }
                hash
            }.sorted().toList()
        }

        for (index in 1 until hashes.size) {
            check(hashes[index - 1] != hashes[index]) {
                "Ad host hash collision detected at sorted index $index"
            }
        }

        val outputFile = outputDirectory.file(AdblockResourceFormat.PATH).get().asFile
        Files.createDirectories(outputFile.parentFile.toPath())
        DataOutputStream(
            BufferedOutputStream(Files.newOutputStream(outputFile.toPath())),
        ).use { output ->
            output.writeInt(AdblockResourceFormat.MAGIC) // HAD1
            output.writeInt(hashes.size)
            hashes.forEach(output::writeLong)
        }
    }
}

abstract class VerifyGeneratedAdblocklistTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedResourcesDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val outputRoot = generatedResourcesDirectory.get().asFile.toPath()
        val generatedFiles = mutableListOf<String>()
        Files.walk(outputRoot).use { paths ->
            paths.filter(Files::isRegularFile).forEach { file ->
                generatedFiles += outputRoot.relativize(file).toString().replace('\\', '/')
            }
        }
        generatedFiles.sort()
        val expectedPath = AdblockResourceFormat.PATH
        check(generatedFiles == listOf(expectedPath)) {
            "Generated Compose resources must contain only $expectedPath; found $generatedFiles"
        }

        val expectedCount = Files.newBufferedReader(
            sourceFile.get().asFile.toPath(),
            StandardCharsets.UTF_8,
        ).useLines { it.count() }
        val outputFile = outputRoot.resolve(expectedPath)
        DataInputStream(
            BufferedInputStream(Files.newInputStream(outputFile)),
        ).use { input ->
            check(input.readInt() == AdblockResourceFormat.MAGIC) {
                "Adblock resource has an invalid magic header"
            }
            val actualCount = input.readInt()
            check(actualCount == expectedCount) {
                "Adblock resource contains $actualCount hashes; expected $expectedCount"
            }
            var previous: Long? = null
            repeat(actualCount) { index ->
                val current = input.readLong()
                check(previous == null || checkNotNull(previous) < current) {
                    "Adblock hashes are not strictly sorted at index $index"
                }
                previous = current
            }
            check(input.read() == -1) { "Adblock resource contains trailing data" }
        }
        val expectedSize = 2L * Int.SIZE_BYTES + expectedCount.toLong() * Long.SIZE_BYTES
        check(Files.size(outputFile) == expectedSize) {
            "Adblock resource size does not match its header"
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val generateAdblocklist = tasks.register<GenerateAdblocklistTask>("generateAdblocklist") {
    sourceFile.set(
        layout.projectDirectory.file("adblock/adblockserverlist.txt"),
    )
    outputDirectory.set(layout.buildDirectory.dir("generated/compose/adblocklist"))
}

val verifyGeneratedAdblocklist =
    tasks.register<VerifyGeneratedAdblocklistTask>("verifyGeneratedAdblocklist") {
        sourceFile.set(generateAdblocklist.flatMap { it.sourceFile })
        generatedResourcesDirectory.set(generateAdblocklist.flatMap { it.outputDirectory })
        dependsOn(generateAdblocklist)
    }

kotlin {
    android {
        namespace = "com.simon.harmonichackernews.shared.resources"
        compileSdk = 37
        minSdk = 26
        androidResources.enable = true

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.multiplatform.resources)
            api(libs.compose.multiplatform.ui)
            api(libs.kmpalette.core)
            implementation(libs.compose.multiplatform.runtime)
        }
    }
}

compose.resources {
    packageOfResClass = "com.simon.harmonichackernews.resources"
    publicResClass = true
    // Keep commonMain on Compose's canonical source directory. The generated binary is an overlay
    // on each leaf platform, so its task no longer has to copy every shared icon, font, and page.
    customDirectory("androidMain", generateAdblocklist.flatMap { it.outputDirectory })
    customDirectory("desktopMain", generateAdblocklist.flatMap { it.outputDirectory })
    customDirectory("iosMain", generateAdblocklist.flatMap { it.outputDirectory })
}

tasks.named("check") {
    dependsOn(verifyGeneratedAdblocklist)
}
