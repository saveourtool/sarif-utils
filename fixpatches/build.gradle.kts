import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem
import org.gradle.nativeplatform.platform.internal.DefaultOperatingSystem
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    application
    id("com.saveourtool.sarifutils.buildutils.kotlin-library")
}

application {
    mainClass.set("com.saveourtool.sarifutils.cli.MainKt")
}

kotlin {
    val os = getCurrentOperatingSystem()

    jvm()

    registerNativeBinaries(os, this)

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.okio)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.sarif4k)
            }
        }
    }

    linkProperExecutable(os)
}

/**
 * @param os
 * @param kotlin
 * @throws GradleException
 */
fun registerNativeBinaries(os: DefaultOperatingSystem, kotlin: KotlinMultiplatformExtension) {
    val saveTarget = when {
        os.isWindows -> kotlin.mingwX64()
        os.isLinux -> kotlin.linuxX64()
        os.isMacOsX -> kotlin.macosX64()
        else -> throw GradleException("Unknown operating system $os")
    }

    configure(listOf(saveTarget)) {
        binaries {
            val name = "sarifutils-${project.version}-${this@configure.name}"
            executable {
                this.baseName = name
                entryPoint = "com.saveourtool.sarifutils.cli.main"
            }
        }
    }
}

/**
 * @param os
 * @throws GradleException
 */
fun linkProperExecutable(os: DefaultOperatingSystem) {
    val linkReleaseExecutableTaskProvider = when {
        os.isLinux -> tasks.getByName("linkReleaseExecutableLinuxX64")
        os.isWindows -> tasks.getByName("linkReleaseExecutableMingwX64")
        os.isMacOsX -> tasks.getByName("linkReleaseExecutableMacosX64")
        else -> throw GradleException("Unknown operating system $os")
    }
    project.tasks.register("linkReleaseExecutableMultiplatform") {
        dependsOn(linkReleaseExecutableTaskProvider)
    }

    // disable building of some binaries to speed up build
    // possible values: `all` - build all binaries, `debug` - build only debug binaries
    val enabledExecutables = if (hasProperty("enabledExecutables")) property("enabledExecutables") as String else null
    if (enabledExecutables != null && enabledExecutables != "all") {
        linkReleaseExecutableTaskProvider.enabled = false
    }
}
