/**
 * Utility methods to work with SARIF files.
 */

package com.saveourtool.sarifutils.cli.utils

import io.github.detekt.sarif4k.ArtifactLocation
import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.Run
import okio.Path
import okio.Path.Companion.toPath

/**
 * @return string with trimmed `file://` or `file:///`
 */
fun String.dropFileProtocol() = substringAfter("file://")
    .let {
        // It is a valid format for Windows paths to look like `file:///C:/stuff`
        if (it[0] == '/' && it[2] == ':') it.drop(1) else it
    }

/**
 * For some reasons, okio does not take into account paths like 'C:/abc/drfd' as absolute, because of slashes `/`
 * replace `/` to the `\\` if any, and call isAbsolute from okio
 *
 * @return whether the path is absolute
 */
fun Path.adaptedIsAbsolute(): Boolean {
    val stringRepresentation = this.toString().dropFileProtocol()
    if (stringRepresentation.length > 2
        && (stringRepresentation.first() in 'a' .. 'z' || stringRepresentation.first() in 'A' .. 'Z')
        && (stringRepresentation.get(1) == ':')
    ) {
        return stringRepresentation.replace('/', '\\').toPath().isAbsolute
    }
    return this.isAbsolute
}

/**
 * `uriBaseID` could be provided directly in `artifactLocation` or in corresponding field from `locations` scope in `results` scope
 *
 * @param result object describes a single result detected by an analysis tool.
 * @return uriBaseID directly from [ArtifactLocation] or from `locations` section, corresponding to this [ArtifactLocation]
 */
fun ArtifactLocation.getUriBaseIdForArtifactLocation(
    result: Result
): String? {
    val uriBaseIdFromLocations = result.locations?.find {
        it.physicalLocation?.artifactLocation?.uri == this.uri
    }
        ?.physicalLocation
        ?.artifactLocation
        ?.uriBaseID
    return this.uriBaseID ?: uriBaseIdFromLocations
}

/**
 * Recursively resolve base uri: https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html#_Toc34317498
 *
 * @param uriBaseId string which indirectly specifies the absolute URI with respect to which that relative reference is interpreted
 * @param run describes a single run of an analysis tool, and contains the reported output of that run
 * @return
 */
fun resolveBaseUri(uriBaseId: String?, run: Run): Path {
    // If `uriBaseID` is not absolute path, then it should be the key from `run.originalURIBaseIDS`;
    // also the tool can set the uriBaseId property to the "%srcroot%" in the absence of `run.originalURIBaseIDS`,
    // which have been agreed that this indicates the root of the source tree in which the file appears.
    val originalUri = if (uriBaseId?.dropFileProtocol()?.toPath()?.adaptedIsAbsolute() == true) {
        return uriBaseId.dropFileProtocol().toPath()
    } else {
        run.originalURIBaseIDS?.get(uriBaseId) ?: return ".".toPath()
    }

    return if (originalUri.uri == null) {
        // base uri is the root
        if (originalUri.uriBaseID == null) {
            ".".toPath()
            // recursively resolve base uri
        } else {
            resolveBaseUri(originalUri.uriBaseID!!, run)
        }
    } else {
        val uri = originalUri.uri!!.dropFileProtocol().toPath()
        // uri is required path
        if (uri.adaptedIsAbsolute()) {
            uri
            // recursively concatenate uri with the base uri
        } else {
            resolveBaseUri(originalUri.uriBaseID, run) / uri
        }
    }
}
