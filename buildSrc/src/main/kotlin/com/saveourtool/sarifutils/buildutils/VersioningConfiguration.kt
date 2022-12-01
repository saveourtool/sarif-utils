/**
 * Version configuration file.
 */

package com.saveourtool.sarifutils.buildutils

import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.gradle.GrgitServiceExtension
import org.ajoberstar.grgit.gradle.GrgitServicePlugin
import org.ajoberstar.reckon.gradle.ReckonExtension
import org.ajoberstar.reckon.gradle.ReckonPlugin
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

/**
 * Configures how project version is determined.
 *
 * @throws GradleException if there was an attempt to run release build with dirty working tree
 */
fun Project.configureVersioning() {
    apply<ReckonPlugin>()
    apply<GrgitServicePlugin>()
    val grgitProvider = project.extensions
        .getByType<GrgitServiceExtension>()
        .service
        .map { it.grgit }

    val isSnapshot = hasProperty("reckon.stage") && property("reckon.stage") == "snapshot"
    configure<ReckonExtension> {
        scopeFromProp()
        if (isSnapshot) {
            // we should build snapshots only for snapshot publishing, so it requires explicit parameter
            snapshotFromProp()
        } else {
            stageFromProp("alpha", "rc", "final")
        }
    }

    // to activate release, provide `-Prelease` or `-Prelease=true`. To deactivate, either omit the property, or set `-Prelease=false`.
    val isRelease = hasProperty("release") && (property("release") as String != "false")
    if (isRelease) {
        failOnUncleanTree(grgitProvider)
    }
    if (isSnapshot) {
        fixForSnapshot(grgitProvider)
    }
}

private fun failOnUncleanTree(grgitProvider: Provider<Grgit>) {
    val grgit = grgitProvider.get()
    val status = grgit.repository.jgit
        .status()
        .call()
    if (!status.isClean) {
        throw GradleException("Release build will be performed with not clean git tree; aborting. " +
                "Untracked files: ${status.untracked}, uncommitted changes: ${status.uncommittedChanges}")
    }
}

/**
 * A terrible hack to remove all pre-release tags. Because in semver `0.1.0-SNAPSHOT` < `0.1.0-alpha`, in snapshot mode
 * we remove tags like `0.1.0-alpha`, and then reckoned version will still be `0.1.0-SNAPSHOT` and it will be compliant.
 */
private fun fixForSnapshot(grgitProvider: Provider<Grgit>) {
    val grgit = grgitProvider.get()
    val preReleaseTagNames = grgit.tag.list()
        .sortedByDescending { it.commit.dateTime }
        .takeWhile {
            // take latest tags that are pre-release
            !it.name.matches(Regex("""^v\d+\.\d+\.\d+$"""))
        }
        .map { it.name }
    grgit.tag.remove { this.names = preReleaseTagNames }
}
