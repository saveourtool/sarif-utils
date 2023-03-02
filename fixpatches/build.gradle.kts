import com.saveourtool.sarifutils.buildutils.configurePublishing

plugins {
    id("com.saveourtool.sarifutils.buildutils.kotlin-library")
    `maven-publish`
}

repositories {
    mavenCentral()
    maven {
        name = "saveourtool/okio-extras"
        url = uri("https://maven.pkg.github.com/saveourtool/okio-extras")
        credentials {
            username = project.findProperty("gprUser") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gprKey") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.okio)
                implementation(libs.okio.extras)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.sarif4k)
                implementation(libs.multiplatform.diff)
                implementation(libs.kotlin.logging)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.log4j.core)
                implementation(libs.log4j.slf4j2.impl)
                implementation(libs.kotest.assertions.core)
            }
        }
    }
}

configurePublishing()
