import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@CacheableTask
abstract class GenerateAdblocklistTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourcesDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val resourceRoot = resourcesDirectory.get().asFile.toPath()
        val outputRoot = outputDirectory.get().asFile.toPath()
        outputRoot.toFile().deleteRecursively()
        Files.walk(resourceRoot).use { paths ->
            paths.forEach { source ->
                val target = outputRoot.resolve(resourceRoot.relativize(source))
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        val hashes = Files.readAllLines(
            sourceFile.get().asFile.toPath(),
            StandardCharsets.UTF_8,
        ).map { host ->
            var hash = -3_750_763_034_362_895_579L
            host.forEach { character ->
                hash = hash xor character.code.toLong()
                hash *= 1_099_511_628_211L
            }
            hash
        }.sorted()

        hashes.zipWithNext().forEachIndexed { index, (first, second) ->
            check(first != second) {
                "Ad host hash collision detected at sorted index ${index + 1}"
            }
        }

        val outputFile = outputDirectory.file(
            "files/adblock/adblockserverlist.bin",
        ).get().asFile
        Files.createDirectories(outputFile.parentFile.toPath())
        DataOutputStream(
            BufferedOutputStream(Files.newOutputStream(outputFile.toPath())),
        ).use { output ->
            output.writeInt(0x48414431) // HAD1
            output.writeInt(hashes.size)
            hashes.forEach(output::writeLong)
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
    resourcesDirectory.set(layout.projectDirectory.dir("src/commonMain/composeResources"))
    sourceFile.set(
        layout.projectDirectory.file("adblock/adblockserverlist.txt"),
    )
    outputDirectory.set(layout.buildDirectory.dir("generated/compose/adblocklist"))
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
    customDirectory("commonMain", generateAdblocklist.flatMap { it.outputDirectory })
}
