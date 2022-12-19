plugins {
    id("com.saveourtool.sarifutils.buildutils.kotlin-library")
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
            }
        }
    }
}
