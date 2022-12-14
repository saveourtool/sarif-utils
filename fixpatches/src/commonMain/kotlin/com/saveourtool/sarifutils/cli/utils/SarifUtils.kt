/**
 * Utility methods to work with SARIF files.
 */

package com.saveourtool.sarifutils.cli.utils

/**
 * @return string with trimmed `file://` or `file:///`
 */
fun String.dropFileProtocol() = substringAfter("file://")
    .let {
        // It is a valid format for Windows paths to look like `file:///C:/stuff`
        if (it[0] == '/' && it[2] == ':') it.drop(1) else it
    }