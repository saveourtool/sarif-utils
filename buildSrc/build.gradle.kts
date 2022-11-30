plugins {
    `kotlin-dsl` version "2.2.0"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // workaround https://github.com/gradle/gradle/issues/15383
    implementation(files(project.libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(libs.diktat.gradle.plugin)
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.22.0")
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.plugin.serialization)
    implementation("org.ajoberstar.reckon:reckon-gradle:0.16.1")
    implementation("com.google.code.gson:gson:2.10")
}
