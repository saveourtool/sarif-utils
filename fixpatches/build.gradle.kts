import com.saveourtool.sarifutils.buildutils.configurePublishing

plugins {
    id("com.saveourtool.sarifutils.buildutils.kotlin-library")
    `maven-publish`
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.okio)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.sarif4k)
                implementation(libs.multiplatform.diff)
                implementation(libs.kotlin.logging)
                implementation(libs.log4j.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.log4j.slf4j2.impl)
            }
        }
    }
}

configurePublishing()
