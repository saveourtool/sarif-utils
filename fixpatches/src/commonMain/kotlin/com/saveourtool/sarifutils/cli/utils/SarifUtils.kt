/**
 * Utility methods to work with SARIF files.
 */

package com.saveourtool.sarifutils.cli.utils

import io.github.detekt.sarif4k.ArtifactLocation
import io.github.detekt.sarif4k.Run
import okio.Path
import okio.Path.Companion.toPath
import io.github.detekt.sarif4k.Result

/**
 * @return string with trimmed `file://` or `file:///`
 */
fun String.dropFileProtocol() = substringAfter("file://")
    .let {
        // It is a valid format for Windows paths to look like `file:///C:/stuff`
        if (it[0] == '/' && it[2] == ':') it.drop(1) else it
    }

/**
 * `uriBaseID` could be provided directly in `artifactLocation` or in corresponding field from `locations` scope in `results` scope
 */
fun ArtifactLocation.getUriBaseIdForArtifactLocation(
    result: Result
): String? {
    val uriBaseIDFromLocations = result.locations?.find {
        it.physicalLocation?.artifactLocation?.uri == this.uri
    }?.physicalLocation?.artifactLocation?.uriBaseID
    return this.uriBaseID ?: uriBaseIDFromLocations
}

// Recursively resolve base uri: https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html#_Toc34317498
fun resolveBaseUri(uriBaseID: String?, run: Run): Path {
    // Find corresponding value in `run.originalURIBaseIDS`, otherwise
    // the tool can set the uriBaseId property to "%srcroot%", which have been agreed that this indicates the root of the source tree in which the file appears.
    val originalUri = if(uriBaseID?.dropFileProtocol()?.toPath()?.isAbsolute == true) {
        return uriBaseID.dropFileProtocol().toPath()
    } else {
        run.originalURIBaseIDS?.get(uriBaseID) ?: return ".".toPath()
    }

    return if (originalUri.uri == null) {
        if (originalUri.uriBaseID == null) {
            ".".toPath()
        } else {
            resolveBaseUri(originalUri.uriBaseID!!, run)
        }
    } else {
        val uri = originalUri.uri!!.dropFileProtocol().toPath()
        if (uri.isAbsolute) {
            uri
        } else {
            resolveBaseUri(originalUri.uriBaseID, run) / uri
        }
    }
}