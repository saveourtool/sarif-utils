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
                implementation(libs.kotlin.logging.jvm)
                implementation(libs.log4j.core)
                implementation(libs.log4j.slf4j.impl)
                implementation(libs.log4j.api)
                implementation(libs.slf4j.api)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.logging.jvm)
                implementation(libs.log4j.core)
                implementation(libs.log4j.slf4j.impl)
                implementation(libs.log4j.api)
                implementation(libs.slf4j.api)
            }
        }
    }
}

configurePublishing()
