rootProject.name = "sarifutils"

include("fixpatches")

plugins {
    id("com.gradle.enterprise") version "3.13.3"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

gradleEnterprise {
    if (System.getenv("CI") != null) {
        buildScan {
            publishAlways()
            termsOfServiceUrl = "https://gradle.com/terms-of-service"
            termsOfServiceAgree = "yes"
        }
    }
}
