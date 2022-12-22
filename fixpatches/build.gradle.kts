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
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.saveourtool.sarif-utils"
            artifactId = "sarif-utils"
            version = version
            from(components["java"])
        }
    }
}

configurePublishing()
